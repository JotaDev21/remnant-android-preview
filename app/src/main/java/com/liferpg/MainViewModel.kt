package com.liferpg

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.liferpg.data.AppRepository
import com.liferpg.data.CameraBossProgress
import com.liferpg.data.ActiveWorkoutStore
import com.liferpg.data.Perfil
import com.liferpg.data.Preferencias
import com.liferpg.data.Refeicao
import com.liferpg.data.RegistroSerie
import com.liferpg.data.ResultadoSerie
import com.liferpg.data.SerieRegistro
import com.liferpg.data.TotalNutricao
import com.liferpg.data.TreinoSessao
import com.liferpg.data.net.Clima
import com.liferpg.data.net.ClimaRepo
import com.liferpg.data.net.NutricaoRepo
import com.liferpg.data.net.Receitas
import com.liferpg.data.net.WgerRepo
import com.liferpg.data.sync.RankingGlobal
import com.liferpg.data.sync.RankingPublicacao
import com.liferpg.data.sync.AmizadeArena
import com.liferpg.data.sync.DesafioArena
import com.liferpg.data.sync.PerfilAmigo
import com.liferpg.data.sync.RecordeAmigo
import com.liferpg.data.sync.IdentidadeAmigo
import com.liferpg.data.sync.PatenteAmigo
import com.liferpg.data.sync.TreinoAmigo
import com.liferpg.data.sync.RotinaAmigo
import com.liferpg.data.sync.Sessao
import com.liferpg.domain.AlimentoEncontrado
import com.liferpg.domain.ClassePersonagem
import com.liferpg.domain.CameraBoss
import com.liferpg.domain.CondicaoSaude
import com.liferpg.domain.ConquistaDef
import com.liferpg.domain.ConquistaEngine
import com.liferpg.domain.EcoPassado
import com.liferpg.domain.EstadoConquistas
import com.liferpg.domain.EstadoSessao
import com.liferpg.domain.EstagioFirmamento
import com.liferpg.domain.Exercicio
import com.liferpg.domain.ExercicioRef
import com.liferpg.domain.Frases
import com.liferpg.domain.HorarioTreino
import com.liferpg.domain.ItemRotinaDef
import com.liferpg.domain.ItemSessao
import com.liferpg.domain.ItemTreino
import com.liferpg.domain.LocalTreino
import com.liferpg.domain.MetaSemana
import com.liferpg.domain.MetasNutricao
import com.liferpg.domain.Musculo
import com.liferpg.domain.NivelExperiencia
import com.liferpg.domain.NutricaoEngine
import com.liferpg.domain.Objetivo
import com.liferpg.domain.Patente
import com.liferpg.domain.PatenteSubida
import com.liferpg.domain.RankEngine
import com.liferpg.domain.Receita
import com.liferpg.domain.ResumoCompetitivo
import com.liferpg.domain.RotinaResolvida
import com.liferpg.domain.rotinaDoDia
import com.liferpg.domain.SerieFeita
import com.liferpg.domain.Sexo
import com.liferpg.domain.Temporadas
import com.liferpg.domain.TituloEngine
import com.liferpg.domain.TreinoEngine
import com.liferpg.domain.TreinoSugerido
import com.liferpg.domain.TrofeuEngine
import com.liferpg.notif.Notificacoes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class MainViewModel(
    private val repo: AppRepository,
    private val nutricaoRepo: NutricaoRepo = Graph.nutricao,
) : ViewModel() {

    private val _eventos = Channel<ResultadoSerie>(Channel.BUFFERED)

    private val activeWorkoutStore = ActiveWorkoutStore(Graph.appContext)
    val circles = com.liferpg.data.CircleController(viewModelScope,Graph.appContext,Graph.auth,Graph.sync,Graph.database,Graph.circleOutbox)
    val cardio = com.liferpg.data.CardioController(viewModelScope, repo, { com.liferpg.data.cardioClock(Graph.appContext) }, com.liferpg.data.CardioTracking(Graph.appContext)) { result ->
        // Existing ascension and streak scenes are shared with cardio, without inventing muscular ranks.
        if (result.nivelNovo > result.nivelAnterior || result.primeiroTreinoDoDia || result.missoesConcluidas.isNotEmpty()) _eventos.send(result)
    }
    private val _sessionSaving = MutableStateFlow(false)
    val sessionSaving = _sessionSaving.asStateFlow()

    val hoje: StateFlow<Long> = flow {
        while (true) {
            emit(LocalDate.now().toEpochDay())
            val agora = LocalDateTime.now()
            val amanha = agora.toLocalDate().plusDays(1).atStartOfDay()
            delay((Duration.between(agora, amanha).toMillis() + 1000).coerceAtMost(60_000))
        }
    }
        .distinctUntilChanged()
        .onEach { repo.garantirMissoes(it)?.let { reward -> _eventos.send(reward) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now().toEpochDay())

    private val _carregando = MutableStateFlow(true)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    val perfil: StateFlow<Perfil?> = repo.perfil
        .onEach { _carregando.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streakEfetivo: StateFlow<Int> = combine(perfil, hoje) { p, dia ->
        if (p == null) 0 else RankEngine.streakVigente(p.streak, p.ultimoDiaTreino, dia)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val exercicios: StateFlow<List<Exercicio>> = flow { emit(repo.catalogo.exercicios()) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cameraBosses: StateFlow<Map<CameraBoss, CameraBossProgress>> = repo.cameraBosses
        .map { rows -> rows.mapNotNull { row ->
            CameraBoss.entries.firstOrNull { it.name == row.bossId }?.let { it to row }
        }.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val historicoValido = repo.validHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val evolutionEntries = repo.evolutionEntries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun registrarRepeticoesCamera(boss: CameraBoss, sessionId: String, total: Int) {
        viewModelScope.launch { runCatching { repo.registrarRepeticoesCamera(boss, sessionId, total) }
            .onFailure { _erros.trySend("Não consegui salvar o progresso do chefe.") } }
    }

    fun registrarProvaCamera(boss: CameraBoss, sessionId: String, state: com.liferpg.domain.CameraTrialState) {
        viewModelScope.launch {
            runCatching { repo.registrarProvaCamera(boss, sessionId, state) }
                .onFailure { _erros.trySend("Não consegui salvar esta tentativa. Tente registrar novamente antes de sair.") }
        }
    }

    fun salvarSerieCamera(exercicio: Exercicio, boss: CameraBoss, sessionId: String, sessionReps: Int, onSaved: () -> Unit,
        trialState: com.liferpg.domain.CameraTrialState? = null) {
        if (!_registrando.compareAndSet(false, true)) {
            _erros.trySend("Aguarde o registro anterior terminar e tente novamente.")
            return
        }
        viewModelScope.launch {
            try {
                if (trialState != null) repo.registrarProvaCamera(boss, sessionId, trialState)
                else repo.registrarRepeticoesCamera(boss, sessionId, sessionReps)
                val pending = repo.repeticoesCameraPendentes(boss).coerceAtMost(com.liferpg.domain.ValidadorTreino.REPS_MAX_CORPORAL)
                if (pending <= 0) { _erros.trySend("Nenhuma repetição pendente."); return@launch }
                val saved = repo.registrarSeriesCameraPendentes(exercicio, boss)
                if (saved.isEmpty()) { _erros.trySend("Crie seu perfil e encerre a tentativa para registrar as séries."); return@launch }
                for ((reps, reg) in saved) when (reg) {
                    is RegistroSerie.Ok -> {
                        _eventos.send(reg.resultado)
                        acumularNaSessao(exercicio, reg.resultado, null, reps, null, autoRest = false)
                        if (reg.suspeito) _erros.trySend("Série salva, mas marcada como suspeita para o ranking.")
                    }
                    is RegistroSerie.Bloqueado -> _erros.trySend(reg.motivo)
                }
                if (saved.all { it.second is RegistroSerie.Ok }) onSaved()
            } catch (error: Exception) {
                Log.e("MainViewModel", "Falha ao salvar série da câmera", error)
                _erros.trySend("Não consegui salvar a série. Seu progresso na câmera foi mantido.")
            } finally { _registrando.value = false }
        }
    }

    val melhoresScores: StateFlow<Map<String, Float>> = repo.melhoresScores
        .map { lista -> lista.associate { it.exercicioId to it.melhor } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val patentes = combine(exercicios, melhoresScores) { catalogo, scores ->
        val porMusculo = catalogo.groupBy { it.musculo }
        Musculo.entries.associateWith {
            RankEngine.patenteDoMusculo(porMusculo[it] ?: emptyList(), scores)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val patenteGeral: StateFlow<Patente> = patentes
        .map { RankEngine.patenteGeral(it.values.map { p -> p.patente }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Patente.SEM_PATENTE)

    val ultimoTreinoPorMusculo: StateFlow<Map<Musculo, Long>> = repo.ultimoTreinoPorMusculo
        .map { lista ->
            lista.mapNotNull {
                runCatching { Musculo.valueOf(it.musculo) to it.ultimoDia }.getOrNull()
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val resumoCompetitivo: StateFlow<ResumoCompetitivo> =
        combine(exercicios, melhoresScores, ultimoTreinoPorMusculo, hoje) { cat, scores, ultimos, dia ->
            RankEngine.resumoCompetitivo(cat.groupBy { it.musculo }, scores, ultimos, dia)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResumoCompetitivo.VAZIO)

    val deltaSemanalPP: StateFlow<Float> = combine(resumoCompetitivo, hoje) { r, dia ->
        r.powerScore to dia
    }.mapLatest { (ps, dia) ->
        ps - (repo.snapshotAteDia(dia - 7)?.powerScore ?: ps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val snapshots = repo.snapshotsRecentes(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val temporadasEncerradas = repo.temporadasEncerradas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val picoTemporada = hoje
        .flatMapLatest { repo.picoTemporada(Temporadas.numeroDaTemporada(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val prefsEfetivas: StateFlow<Preferencias> =
        combine(repo.preferencias, perfil) { p, perf ->
            p ?: TreinoEngine.preferenciasPadrao(perf?.pesoCorporalKg ?: 70f)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TreinoEngine.preferenciasPadrao(70f),
        )

    val treinoSugerido =
        combine(
            prefsEfetivas,
            exercicios,
            resumoCompetitivo,
            ultimoTreinoPorMusculo,
            hoje,
        ) { prefs, cat, resumo, ultimos, dia ->
            TreinoEngine.gerar(prefs, cat.groupBy { it.musculo }, resumo.ratings, ultimos, dia)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TreinoSugerido.VAZIO)

    private val _sessao = MutableStateFlow<EstadoSessao?>(null)
    val sessao: StateFlow<EstadoSessao?> = _sessao.asStateFlow()
    val sessionShareable = sessao.mapLatest { s: EstadoSessao? ->
        if(s!=null && s.concluida) repo.resumoCompartilhavel(s.sessionUuid) else false
    }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),false)

    private fun atualizarSessao(session: EstadoSessao?) {
        _sessao.value = session
        activeWorkoutStore.save(session)
    }

    val sessoesRecentes = repo.sessoesRecentes(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val arquivoTreinos = combine(repo.arquivoSessoes,repo.arquivoSeries,exercicios) { sessions,sets,catalog ->
        com.liferpg.domain.WorkoutArchive(sessions.map(com.liferpg.data.WorkoutArchiveReader::workout),
            com.liferpg.domain.WorkoutArchiveEngine.days(sets,catalog))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),null)

    val pesoHistorico = hoje
        .flatMapLatest { repo.pesoDesde(it - 120) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val metasSemana: StateFlow<MetaSemana> = hoje
        .flatMapLatest { dia ->
            val inicio = dia - (java.time.LocalDate.ofEpochDay(dia).dayOfWeek.value - 1)
            combine(repo.seriesDesde(inicio), prefsEfetivas, cardio.history) { rows, prefs, cardioRows ->
                val series = rows.filter { it.valido && it.epochDay <= dia }
                val treinos = (series.map { it.epochDay } + cardioRows.filter { it.epochDay in inicio..dia && it.accumulatedMs >= com.liferpg.domain.CardioEngine.STREAK_SECONDS * 1000L }.map { it.epochDay }).distinct().size
                val metaTreinos = prefs.diasPorSemana.coerceAtLeast(1)
                MetaSemana(treinos, metaTreinos, series.size, metaTreinos * 12)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetaSemana(0, 3, 0, 36))

    val diasTreinadosRecentes = hoje.flatMapLatest { day ->
        combine(repo.seriesDesde(day - 6), cardio.history) { rows, cardioRows ->
            (rows.filter { it.valido && it.epochDay <= day }.map { it.epochDay } +
                cardioRows.filter { it.epochDay in day-6..day && it.accumulatedMs >= com.liferpg.domain.CardioEngine.STREAK_SECONDS * 1000L }.map { it.epochDay }).toSet()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val revisaoSemanal = combine(historicoValido, hoje, prefsEfetivas, cardio.history) { rows, day, settings, movements ->
        com.liferpg.domain.EvolutionEngine.week(rows, day, settings.diasPorSemana, com.liferpg.domain.CardioEngine.qualifyingDays(movements))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly,
        com.liferpg.domain.EvolutionEngine.week(emptyList(), hoje.value, 3))

    val jornadas = combine(evolutionEntries, historicoValido, hoje, cardio.history) { entries, rows, day, movements ->
        entries.filter { it.id.startsWith("campaign:") }.mapNotNull { row ->
            runCatching { com.liferpg.domain.EvolutionEngine.campaign(com.liferpg.domain.EvolutionCodec.campaign(row), rows, day, movements) }.getOrNull()
        }.sortedByDescending { it.campaign.createdAt }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val arquivoSemanal = combine(historicoValido, hoje, prefsEfetivas, cardio.history) { rows, day, settings, movements ->
        com.liferpg.domain.WeeklyArchive.build(rows, day, settings.diasPorSemana, 9, com.liferpg.domain.CardioEngine.qualifyingDays(movements))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun iniciarJornada(nome: String, semanas: Int, meta: Int) = lancar("Não consegui iniciar a jornada.") {
        repo.startCampaign(nome, hoje.value, semanas, meta)
    }
    fun arquivarJornada(c: com.liferpg.domain.PersonalCampaign) = lancar("Não consegui encerrar a jornada.") {
        repo.saveEvolution(com.liferpg.domain.EvolutionCodec.encodeCampaign(c.copy(archived = true, closedAt = System.currentTimeMillis())))
    }
    fun equipamentoDo(id: String) = runCatching {
        com.liferpg.domain.EvolutionCodec.equipment(evolutionEntries.value.firstOrNull { it.id == "equipment:$id" })
    }.getOrDefault(com.liferpg.domain.EquipmentSetup())
    fun salvarEquipamento(id: String, setup: com.liferpg.domain.EquipmentSetup) = lancar("Não consegui salvar o equipamento.") {
        repo.saveEvolution(com.liferpg.domain.EvolutionCodec.encodeEquipment(id, setup))
    }
    fun alvoDo(ex: Exercicio): com.liferpg.domain.TrainingTarget {
        val active = sessao.value?.takeUnless { it.concluida }
        val planned = active?.itens?.firstOrNull { it.item.exercicio.id == ex.id }?.item
            ?: treinoHoje.value.itens.firstOrNull { it.exercicio.id == ex.id }
            ?: Objetivo.seguro(prefsEfetivas.value.objetivo).let { goal -> ItemTreino(ex, goal.seriesBase, goal.repMin, goal.repMax, goal.descansoSeg, "Seu objetivo") }
        val cutoff = active?.iniciadaEm ?: LocalDate.ofEpochDay(hoje.value).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val setup = equipamentoDo(ex.id)
        return com.liferpg.domain.AdaptiveProgression.target(planned, historicoValido.value, setup, cutoff, hoje.value,
            historicoValido.value.filter { it.exercicioId == ex.id && it.timestamp >= cutoff && it.equipamento == setup.label })
    }
    fun alternativasPara(ex: Exercicio): List<Exercicio> {
        val inSession = sessao.value?.itens?.map { it.item.exercicio.id }.orEmpty().toSet()
        return exercicios.value.filter { it.id != ex.id && it.musculo == ex.musculo && it.id !in inSession &&
            it.modo == ex.modo && (prefsEfetivas.value.localPreferido != "CASA" || it.local != LocalTreino.ACADEMIA) }
            .sortedWith(compareBy<Exercicio> { it.equipamento == ex.equipamento }.thenBy { it.dificuldade }).take(12)
    }
    fun substituirExercicio(ex: Exercicio) {
        val s = sessao.value ?: return
        val current = s.itemAtual ?: return
        if (s.concluida || current.feitas.isNotEmpty() || current.item.exercicio.musculo != ex.musculo || s.itens.any { it.item.exercicio.id == ex.id }) {
            avisar("Troque antes de registrar séries. Os resultados anteriores precisam manter seu exercício original."); return
        }
        val items = s.itens.toMutableList()
        items[s.indiceAtual] = current.copy(item = current.item.copy(exercicio = ex, motivo = "Substituição escolhida por você"))
        atualizarSessao(s.copy(itens = items))
    }

    init {
        viewModelScope.launch {
            prefsEfetivas.collect { prefs ->
                val ctx = Graph.appContext
                if (prefs.lembretesAtivos) {
                    Notificacoes.agendarLembreteDiario(
                        ctx,
                        HorarioTreino.seguro(prefs.horarioTreino).hora,
                    )
                } else {
                    Notificacoes.cancelarLembrete(ctx)
                }
            }
        }
    }

    fun atualizarPreferencias(p: Preferencias) =
        lancar("Não consegui salvar suas preferências.") { repo.salvarPreferencias(p) }

    val rotinas: StateFlow<List<RotinaResolvida>> =
        combine(repo.rotinas, exercicios) { lista, cat ->
            val porId = cat.associateBy { it.id }
            lista.map { r ->
                RotinaResolvida(
                    r.id,
                    r.nome,
                    repo.decodificarItensRotina(r.itensJson).mapNotNull { d ->
                        porId[d.exercicioId]?.let {
                            ItemTreino(it, d.series, d.repMin, d.repMax, d.descansoSeg, "Sua rotina")
                        }
                    },
                    repo.decodificarDiasRotina(r.itensJson),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun construirRecordes(records: List<com.liferpg.data.RecordeExercicio>, catalog: List<Exercicio>): List<RecordeAmigo> {
        val byId = catalog.associateBy { it.id }
        return records.mapNotNull { row -> byId[row.exercicioId]?.let { ex ->
            val rating = RankEngine.ratingDoScore(row.melhorScore, ex.benchmarks)
            val rank = RankEngine.patenteDeRating(rating)
            val rankLabel = rank.titulo + if (rank == Patente.SEM_PATENTE) "" else " ${com.liferpg.domain.Divisao.doOffset(rating - rank.ordinal * 1000f).romano}"
            RecordeAmigo(ex.id, ex.nome, ex.musculo.nomeExibicao, ex.modo.name, row.pesoKg,
                row.reps ?: 0, row.segundos ?: 0, row.epochDay,
                rankLabel, row.series)
        } }
    }

    val meusRecordes: StateFlow<List<RecordeAmigo>> = combine(repo.recordesExercicios, exercicios, ::construirRecordes)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun copiarTreinoAmigo(routine: RotinaAmigo, nickname: String, onCopied: () -> Unit = {}) = lancar("Não consegui copiar esse treino.") {
        val items = com.liferpg.domain.CompeticaoAmigos.itensImportaveis(routine, repo.catalogo.exercicios())
        require(items.isNotEmpty()) { "Este aliado precisa atualizar o app para compartilhar a rotina completa." }
        // Copy unscheduled: adopting a friend's plan cannot displace the user's existing weekdays.
        val name = "${routine.nome} · $nickname".take(60)
        val exists = repo.rotinas.first().any { it.nome == name && repo.decodificarItensRotina(it.itensJson) == items }
        if (exists) _erros.trySend("Esse treino já está no seu planejamento.") else {
            repo.salvarRotina(name, items)
            val skipped = routine.itens.size - items.size
            _erros.trySend(if (skipped > 0) "Treino copiado. $skipped exercícios indisponíveis foram omitidos; revise no planejamento." else "Treino copiado! Escolha os dias em Montar treino.")
        }
        onCopied()
    }

    fun duplicarRotina(routine: RotinaResolvida) = lancar("Não consegui duplicar a rotina.") {
        repo.salvarRotina("${routine.nome} · cópia".take(60), routine.itens.map {
            ItemRotinaDef(it.exercicio.id, it.series, it.repMin, it.repMax, it.descansoSeg)
        })
        _erros.trySend("Cópia criada sem alterar os dias do treino original.")
    }

    fun repetirSessao(session: TreinoSessao, onStarted: () -> Unit = {}) = lancar("Não consegui preparar esse treino.") {
        val stored = repo.decodificarItensRotina(session.itensJson)
        if (stored.isNotEmpty()) {
            val byId = repo.catalogo.exercicios().associateBy { it.id }
            val items = stored.mapNotNull { d -> byId[d.exercicioId]?.let { ItemTreino(it, d.series, d.repMin, d.repMax, d.descansoSeg, "Treino repetido") } }
            require(items.isNotEmpty())
            if (iniciarSessao(session.nome, items)) onStarted()
            return@lancar
        }
        val routine = rotinas.value.firstOrNull { it.nome == session.nome }
        if (routine != null) { if (iniciarSessao(routine.nome, routine.itens)) onStarted() }
        else {
            val catalog = repo.catalogo.exercicios().associateBy { it.id }
            // Legacy history has day-only sets. Ambiguous days must never invent a routine.
            val sameDay = repo.sessoesRecentes(1000).first().count { it.epochDay == session.epochDay }
            require(sameDay == 1) { "Mais de uma sessão nesse dia. Repita pelo planejamento para usar os exercícios certos." }
            val sets = repo.seriesDoDia(session.epochDay).first().filter { it.valido }.groupBy { it.exercicioId }
            val items = sets.mapNotNull { (id, rows) -> catalog[id]?.let { ex ->
                val reps = rows.mapNotNull { it.reps ?: it.segundos?.div(3) }.filter { it > 0 }
                ItemTreino(ex, rows.size.coerceIn(1, 10), (reps.minOrNull() ?: 8).coerceIn(1, 100), (reps.maxOrNull() ?: 12).coerceIn(1, 100), prefsEfetivas.value.descansoPadraoSeg, "Repetição do histórico")
            } }
            require(items.isNotEmpty())
            if (iniciarSessao(session.nome, items)) onStarted()
        }
    }

    val treinoHoje: StateFlow<TreinoSugerido> =
        combine(rotinas, treinoSugerido, hoje) { saved, suggested, day ->
            rotinaDoDia(saved, day)?.let { routine ->
                TreinoSugerido(
                    titulo = routine.nome,
                    subtitulo = "Seu treino configurado para hoje",
                    itens = routine.itens,
                    musculosAlvo = routine.itens.map { it.exercicio.musculo }.distinct(),
                )
            } ?: suggested
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TreinoSugerido.VAZIO)

    fun iniciarSessao(nome: String, itens: List<ItemTreino>): Boolean {
        if (itens.isEmpty()) return false
        if (_sessao.value?.concluida == false) {
            _erros.trySend("Você já tem um treino em andamento. Finalize-o antes de iniciar outro.")
            return false
        }
        atualizarSessao(EstadoSessao(
            nome = nome.ifBlank { "Treino do dia" },
            itens = itens.map { ItemSessao(it) },
            iniciadaEm = System.currentTimeMillis(),
        ))
        return true
    }

    fun iniciarSessaoSugerida() {
        val t = treinoHoje.value
        iniciarSessao(t.titulo.ifBlank { "Treino do dia" }, t.itens)
    }

    fun iniciarSessaoDeIds(nome: String, ids: List<String>) {
        val porId = exercicios.value.associateBy { it.id }
        val obj = Objetivo.seguro(prefsEfetivas.value.objetivo)
        iniciarSessao(
            nome,
            ids.distinct().mapNotNull { porId[it] }.map {
                ItemTreino(it, obj.seriesBase, obj.repMin, obj.repMax, obj.descansoSeg, "Seu treino")
            },
        )
    }

    fun irParaItemSessao(i: Int) {
        val s = _sessao.value ?: return
        if (i in s.itens.indices) atualizarSessao(s.copy(indiceAtual = i))
    }

    fun definirConclusaoItemSessao(i: Int, concluido: Boolean) {
        val s = _sessao.value ?: return
        if (s.concluida || i !in s.itens.indices) return
        val novos = s.itens.toMutableList()
        novos[i] = novos[i].copy(conclusaoManual = concluido)
        atualizarSessao(s.copy(itens = novos))
    }

    fun proximoExercicioSessao() {
        _sessao.value?.let { irParaItemSessao((it.indiceAtual + 1).coerceAtMost(it.itens.size - 1)) }
    }

    fun exercicioAnteriorSessao() {
        _sessao.value?.let { irParaItemSessao((it.indiceAtual - 1).coerceAtLeast(0)) }
    }

    fun acumularNaSessao(
        exercicio: Exercicio,
        r: ResultadoSerie,
        peso: Float?,
        reps: Int?,
        seg: Int?,
        autoRest: Boolean = true,
    ) {
        val s = _sessao.value ?: return
        if (s.concluida) return
        val idx = s.indiceAtual.takeIf { s.itens.getOrNull(it)?.item?.exercicio?.id == exercicio.id }
            ?: s.itens.indexOfFirst { it.item.exercicio.id == exercicio.id }
        if (idx < 0) return
        val subiu = r.patenteNova > r.patenteAnterior
        val novos = s.itens.toMutableList()
        val alvo = novos[idx]
        novos[idx] = alvo.copy(feitas = alvo.feitas + SerieFeita(peso, reps, seg, r.novoRecorde))
        atualizarSessao(s.copy(
            itens = novos,
            xpGanho = s.xpGanho + r.xpGanho,
            recordes = if (r.novoRecorde) (s.recordes + exercicio.nome).distinct() else s.recordes,
            patentesSubidas = if (subiu) {
                (s.patentesSubidas.filterNot { it.musculo == exercicio.musculo } +
                    PatenteSubida(exercicio.musculo, r.patenteNova))
            } else {
                s.patentesSubidas
            },
        ))
        if(autoRest) iniciarDescanso(novos[idx].item.descansoSeg)
    }

    fun finalizarSessao() {
        cancelarDescanso()
        val s = _sessao.value ?: return
        if (s.concluida || _sessionSaving.value) return
        val finalizada = s.copy(concluida = true)
        if (finalizada.seriesFeitas==0) { atualizarSessao(finalizada); return }
        _sessionSaving.value=true
        if (finalizada.seriesFeitas > 0) {
            viewModelScope.launch {
                runCatching {
                    val endedAt=System.currentTimeMillis()
                    repo.salvarSessao(
                        TreinoSessao(
                            epochDay = hoje.value,
                            nome = finalizada.nome,
                            xpGanho = finalizada.xpGanho,
                            series = finalizada.seriesFeitas,
                            exercicios = finalizada.exerciciosConcluidos,
                            recordes = finalizada.recordes.size,
                            duracaoSeg = ((System.currentTimeMillis() - finalizada.iniciadaEm) / 1000).toInt(),
                            encerradaEm = endedAt,
                            sessionUuid = finalizada.sessionUuid,
                            receiptJson = runCatching { com.liferpg.data.sync.CircleCodec.receipt(finalizada,endedAt) }.getOrDefault(""),
                            itensJson = repo.codificarItensRotina(finalizada.itens.map { it.item }.map {
                                ItemRotinaDef(it.exercicio.id, it.series, it.repMin, it.repMax, it.descansoSeg)
                            }),
                        ),
                    )
                    atualizarSessao(finalizada)
                    if (sessaoGlobal.value != null) publicarRankingAgora()
                }.onFailure { _erros.trySend("As séries estão guardadas, mas não consegui salvar o resumo. Tente concluir novamente.") }
                _sessionSaving.value=false
            }
        }
    }

    fun encerrarSessao() {
        cancelarDescanso()
        atualizarSessao(null)
    }

    fun salvarRotina(
        nome: String,
        itens: List<ItemRotinaDef>,
        diasSemana: Set<Int>,
        id: Long = 0L,
    ) = lancar("Não consegui salvar a rotina.") {
        // A weekday belongs to one routine. Moving it here removes it from
        // older routines so the daily plan is deterministic.
        rotinas.value
            .filter { it.id != id && it.diasSemana.any(diasSemana::contains) }
            .forEach { antiga ->
                repo.salvarRotina(
                    antiga.nome,
                    antiga.itens.map { item ->
                        ItemRotinaDef(
                            item.exercicio.id,
                            item.series,
                            item.repMin,
                            item.repMax,
                            item.descansoSeg,
                        )
                    },
                    antiga.diasSemana - diasSemana,
                    antiga.id,
                )
            }
        repo.salvarRotina(nome, itens, diasSemana, id)
    }

    fun apagarRotina(id: Long) =
        lancar("Não consegui apagar a rotina.") { repo.apagarRotina(id) }

    val refeicoesHoje = hoje
        .flatMapLatest { repo.refeicoesDoDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nutricaoHoje = hoje
        .flatMapLatest { repo.totalNutricaoDoDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TotalNutricao(0f, 0f))

    private val _diaNutricao = MutableStateFlow<Long?>(null)
    val diaNutricao = combine(hoje, _diaNutricao) { today, selected -> selected ?: today }
        .stateIn(viewModelScope, SharingStarted.Eagerly, hoje.value)
    fun selecionarDiaNutricao(dia: Long) { _diaNutricao.value = dia.takeUnless { it == hoje.value } }
    val refeicoesNutricao = diaNutricao.flatMapLatest { repo.refeicoesDoDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val historicoNutricao = hoje.flatMapLatest { repo.refeicoesPeriodo(it - 29, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val aguaNutricao = diaNutricao.flatMapLatest { repo.aguaDoDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val alimentosSalvos = repo.alimentosSalvos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun registrarAgua(ml: Int) { val day = diaNutricao.value; lancar("Não consegui salvar a água.") { repo.registrarAgua(day, ml)?.let { _eventos.send(it) } } }
    fun desfazerAgua() { val day = diaNutricao.value; lancar("Não consegui desfazer o registro.") { repo.desfazerAgua(day) } }
    fun repetirRefeicao(r: Refeicao, categoria: String = r.categoria) {
        val day = diaNutricao.value
        lancar("Não consegui repetir esta refeição.") { repo.adicionarRefeicao(r.copy(id = 0, registroId = null, epochDay = day, timestamp = System.currentTimeMillis(), categoria = categoria)) }
    }
    private val salvandoAlimento = MutableStateFlow(false)
    fun editarRefeicao(r: Refeicao, onError: () -> Unit = {}, onSaved: () -> Unit) {
        escreverAlimento(onError, onSaved) { repo.editarRefeicao(r) }
    }
    private fun escreverAlimento(onError: () -> Unit, onSaved: () -> Unit, write: suspend () -> Unit) {
        if (!salvandoAlimento.compareAndSet(false, true)) { onError(); _erros.trySend("Aguarde o registro anterior terminar."); return }
        viewModelScope.launch {
            try { write(); onSaved() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onError(); _erros.trySend("Não consegui salvar a refeição. Sua revisão foi mantida.") }
            finally { salvandoAlimento.value = false }
        }
    }
    fun salvarFavorito(food: AlimentoEncontrado, grams: Float) {
        val key = food.id.takeIf { it != "off:" }?.ifBlank { null } ?: "manual:${com.liferpg.domain.normalizarAlimento(food.nome)}:${food.fonte}"
        lancar("Não consegui salvar o alimento.") {
            repo.salvarAlimento(com.liferpg.data.AlimentoSalvo(key, food.nome, com.liferpg.domain.NutritionFoodCodec.escrever(food), grams))
            _erros.trySend("Alimento salvo para repetir depois.")
        }
    }
    fun apagarFavorito(food: com.liferpg.data.AlimentoSalvo) = lancar("Não consegui remover o alimento salvo.") { repo.apagarAlimento(food) }
    fun alimentosBrasileiros(query: String) = nutricaoRepo.brasileiros?.buscar(query).orEmpty()
    fun salvarMetasNutri(p: Preferencias, idade: Int, altura: Int, sexo: String, onError: () -> Unit, onSaved: () -> Unit) {
        viewModelScope.launch {
            try { repo.salvarMetasNutri(p, idade, altura, sexo); onSaved() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { onError(); _erros.trySend("Não consegui salvar suas referências. Tente novamente.") }
        }
    }

    private val plateStore = com.liferpg.data.NutritionDraftStore(Graph.appContext)
    private val _pratoRevisao = MutableStateFlow(plateStore.ler())
    val pratoRevisao = _pratoRevisao.asStateFlow()
    private val _analisandoPrato = MutableStateFlow(false)
    val analisandoPrato = _analisandoPrato.asStateFlow()
    private val _erroPrato = MutableStateFlow<String?>(null)
    val erroPrato = _erroPrato.asStateFlow()
    private val _salvandoPrato = MutableStateFlow(false)
    val salvandoPrato = _salvandoPrato.asStateFlow()
    fun alimentoDoPrato(id: String?) = nutricaoRepo.brasileiros?.obter(id)
    fun editarPrato(index: Int, item: com.liferpg.data.ItemPratoRevisao) {
        val draft = _pratoRevisao.value ?: return
        if (_salvandoPrato.value) return
        if (index !in draft.itens.indices) return
        val updated = draft.copy(itens = draft.itens.mapIndexed { i, old -> if (i == index) item else old })
        _pratoRevisao.value = updated; plateStore.salvar(updated)
    }
    fun adicionarItemPrato(food: AlimentoEncontrado) {
        val draft = _pratoRevisao.value ?: return
        if (_salvandoPrato.value || draft.itens.size >= 6 || alimentoDoPrato(food.id) == null) return
        val item = com.liferpg.data.ItemPratoRevisao(food.nome, food.id, "100", "confirmada")
        val updated = draft.copy(itens = draft.itens + item)
        _pratoRevisao.value = updated; plateStore.salvar(updated)
    }
    fun descartarPrato() { if (!_analisandoPrato.value && !_salvandoPrato.value) { _pratoRevisao.value = null; plateStore.salvar(null); _erroPrato.value = null } }
    fun analisarPrato(uri: android.net.Uri) {
        if (_salvandoPrato.value || !_analisandoPrato.compareAndSet(false, true)) return
        _erroPrato.value = null
        viewModelScope.launch {
            try {
                val api = com.liferpg.data.net.RemMentor.fromBuildConfig()
                val budget = com.liferpg.data.net.MentorDailyBudget(Graph.appContext)
                budget.requireAvailable()
                val credential = withContext(Dispatchers.IO) {
                    if (api.usesProxy) com.liferpg.data.net.MentorInstallToken(Graph.appContext).getOrCreate()
                    else com.liferpg.data.net.GeminiKey(Graph.appContext).let { key ->
                        if (!key.configured()) throw com.liferpg.data.net.MentorFailure("A análise de imagens ainda não está disponível nesta versão. Use a busca de alimentos.")
                        key.read()
                    }
                }
                val jpeg = com.liferpg.data.net.NutritionPhoto.preparar(Graph.appContext, uri)
                val analysis = api.analisarPrato(credential, jpeg, nutricaoRepo.brasileiros ?: error("Base brasileira indisponível"))
                budget.recordSuccess()
                if (analysis.componentes.isEmpty()) { _erroPrato.value = analysis.observacao.ifBlank { "Mostre o prato inteiro, com boa luz, visto de cima." }; return@launch }
                val draft = plateStore.criar(analysis, uri.toString())
                _pratoRevisao.value = draft; plateStore.salvar(draft)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: com.liferpg.data.net.MentorFailure) { _erroPrato.value = e.explanation
            } catch (e: Exception) { _erroPrato.value = if (e is IllegalArgumentException) e.message else "Não consegui analisar esta foto. Tente novamente ou use a busca de alimentos." }
            finally { _analisandoPrato.value = false }
        }
    }
    fun confirmarPrato(categoria: String, onSaved: () -> Unit) {
        if (!_salvandoPrato.compareAndSet(false, true)) return
        val draft = _pratoRevisao.value
        val day = diaNutricao.value
        viewModelScope.launch {
            try {
                val included = draft?.itens?.mapIndexed { i, item -> i to item }?.filter { it.second.incluido }.orEmpty()
                require(included.isNotEmpty()) { "Inclua ao menos um alimento." }
                val rows = included.map { (index, item) ->
                    val food = alimentoDoPrato(item.alimentoId) ?: error("Escolha a referência de ${item.nome} antes de salvar.")
                    val grams = item.gramas ?: error("Corrija a porção de ${item.nome}.")
                    refeicaoDe(food, grams, day, categoria, estimado = true).copy(registroId = "plate:${draft!!.id}:$index")
                }
                repo.adicionarPrato(rows)
                _pratoRevisao.value = null; plateStore.salvar(null); onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) { _erroPrato.value = e.message ?: "Não consegui salvar o prato. Sua revisão foi mantida." }
            finally { _salvandoPrato.value = false }
        }
    }

    val metasNutricao: StateFlow<MetasNutricao> = combine(perfil, prefsEfetivas) { p, prefs ->
        if (p == null) MetasNutricao(0, prefs.metaProteinaG, 0, 0, 0)
        else NutricaoEngine.calcular(p, prefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MetasNutricao(0, 0, 0, 0, 0))

    val metaProteina: StateFlow<Int> = metasNutricao
        .map { it.proteinaG }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _resultadosBusca = MutableStateFlow<List<AlimentoEncontrado>>(emptyList())
    val resultadosBusca: StateFlow<List<AlimentoEncontrado>> = _resultadosBusca.asStateFlow()

    private val _buscandoAlimento = MutableStateFlow(false)
    val buscandoAlimento: StateFlow<Boolean> = _buscandoAlimento.asStateFlow()

    private val _alimentoEscaneado = MutableStateFlow<AlimentoEncontrado?>(null)
    val alimentoEscaneado: StateFlow<AlimentoEncontrado?> = _alimentoEscaneado.asStateFlow()

    private val _receitas = MutableStateFlow<List<Receita>>(emptyList())
    val receitas: StateFlow<List<Receita>> = _receitas.asStateFlow()
    val receitasLocais: List<Receita> by lazy {
        nutricaoRepo.brasileiros?.let(com.liferpg.domain.RecipeBook::criar).orEmpty()
    }
    private val _erroReceitas = MutableStateFlow<String?>(null)
    val erroReceitas = _erroReceitas.asStateFlow()
    private var receitasJob: Job? = null
    private var receitasGeneration = 0

    private val _buscandoReceitas = MutableStateFlow(false)
    val buscandoReceitas: StateFlow<Boolean> = _buscandoReceitas.asStateFlow()

    private var foodSearchJob: kotlinx.coroutines.Job? = null
    private var foodSearchGeneration = 0
    private val _avisoBuscaAlimento = MutableStateFlow<String?>(null)
    val avisoBuscaAlimento = _avisoBuscaAlimento.asStateFlow()
    fun buscarAlimento(termo: String, online: Boolean = false) {
        foodSearchJob?.cancel()
        val generation = ++foodSearchGeneration
        if (termo.isBlank()) {
            _resultadosBusca.value = emptyList()
            return
        }
        foodSearchJob = viewModelScope.launch {
            _buscandoAlimento.value = true
            try {
                val res = nutricaoRepo.buscar(termo.trim(), online).getOrThrow()
                _resultadosBusca.value = res.alimentos
                _avisoBuscaAlimento.value = res.aviso ?: if (res.alimentos.isEmpty()) "Nenhum resultado. Tente outro preparo ou registre pelo rótulo." else null
            } finally { if (generation == foodSearchGeneration) _buscandoAlimento.value = false }
        }
    }

    fun limparBuscaAlimento() {
        foodSearchJob?.cancel(); foodSearchGeneration++; _buscandoAlimento.value = false; _avisoBuscaAlimento.value = null
        _resultadosBusca.value = emptyList()
    }

    private val _buscandoBarcode = MutableStateFlow(false)
    val buscandoBarcode = _buscandoBarcode.asStateFlow()
    fun buscarPorBarcode(codigo: String) {
        if (!_buscandoBarcode.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
            nutricaoRepo.porBarcode(codigo)
                .onSuccess {
                    if (it != null) _alimentoEscaneado.value = it
                    else _erros.trySend("Produto não encontrado no Open Food Facts.")
                }
                .onFailure { _erros.trySend("Falha ao ler o código. Verifique a conexão.") }
            } finally { _buscandoBarcode.value = false }
        }
    }

    fun limparEscaneado() {
        _alimentoEscaneado.value = null
    }

    fun buscarReceitas(termo: String) {
        receitasJob?.cancel()
        val generation = ++receitasGeneration
        _buscandoReceitas.value = true
        _receitas.value = emptyList()
        _erroReceitas.value = null
        receitasJob = viewModelScope.launch {
            try {
                val result = Receitas.buscar(termo.take(100))
                if (generation == receitasGeneration) result
                    .onSuccess { _receitas.value = it; if (it.isEmpty()) _erroReceitas.value = "Nenhuma receita online encontrada. Tente frango, arroz, ovos ou o nome em inglês. As receitas do REM continuam disponíveis." }
                    .onFailure { _erroReceitas.value = "Não consegui consultar receitas online. As ideias do REM continuam disponíveis sem internet." }
            } finally { if (generation == receitasGeneration) _buscandoReceitas.value = false }
        }
    }
    fun limparReceitasOnline() {
        receitasJob?.cancel(); receitasGeneration++
        _receitas.value = emptyList(); _erroReceitas.value = null; _buscandoReceitas.value = false
    }

    private val _clima = MutableStateFlow<Clima?>(null)
    val clima: StateFlow<Clima?> = _clima.asStateFlow()

    init {
        viewModelScope.launch {
            ClimaRepo.obter().onSuccess { _clima.value = it }
        }
    }

    private val _wger = MutableStateFlow<List<ExercicioRef>>(emptyList())
    val wger: StateFlow<List<ExercicioRef>> = _wger.asStateFlow()

    private val _buscandoWger = MutableStateFlow(false)
    val buscandoWger: StateFlow<Boolean> = _buscandoWger.asStateFlow()

    val categoriasWger = WgerRepo.CATEGORIAS

    fun carregarCategoriaWger(categoriaId: Int) {
        viewModelScope.launch {
            _buscandoWger.value = true
            WgerRepo.porCategoria(categoriaId)
                .onSuccess { _wger.value = it }
                .onFailure { _erros.trySend("Não consegui carregar o wger. Verifique a conexão.") }
            _buscandoWger.value = false
        }
    }

    fun avisar(msg: String) {
        _erros.trySend(msg)
    }

    private val _passosHoje = MutableStateFlow(0L)
    val passosHoje: StateFlow<Long> = _passosHoje.asStateFlow()

    val healthDisponivel: Boolean get() = Graph.health.disponivel
    val permissoesHealth: Set<String> get() = Graph.health.permissoes

    fun sincronizarSaude() {
        viewModelScope.launch {
            if (!Graph.health.temAlgumaPermissao()) {
                _erros.trySend("Permissão do Health Connect não concedida.")
                return@launch
            }
            val peso = Graph.health.ultimoPesoKg()
            val passos = Graph.health.passosHoje()
            _passosHoje.value = passos
            if (peso != null) repo.atualizarPeso(peso)
            _erros.trySend(
                if (peso != null) "Importado: ${peso.toInt()}kg · $passos passos hoje ✅"
                else if (passos > 0) "$passos passos hoje · sem peso no Health Connect"
                else "Conectado, mas ainda não há dados no Health Connect.",
            )
        }
    }

    private fun refeicaoDe(alimento: AlimentoEncontrado, porcaoG: Float, day: Long, categoria: String, estimado: Boolean = false): Refeicao {
        require(alimento.dadosAusentes == 0) { "Complete os macros antes de registrar." }
        val fator = porcaoG / 100f
        return Refeicao(epochDay = day, nome = alimento.nome.take(160), proteinaG = alimento.proteinaG * fator,
            calorias = alimento.calorias * fator, carboG = alimento.carboG * fator, gorduraG = alimento.gorduraG * fator,
            porcaoG = porcaoG, barcode = alimento.barcode, timestamp = System.currentTimeMillis(), categoria = categoria,
            fonte = alimento.fonte, estimado = estimado, fibraG = alimento.fibraG?.times(fator), macrosCompletos = true)
    }
    fun adicionarAlimento(alimento: AlimentoEncontrado, porcaoG: Float, categoria: String = "OUTROS", onError: () -> Unit = {}, onSaved: () -> Unit = {}) {
        val day = diaNutricao.value
        escreverAlimento(onError, onSaved) {
            repo.adicionarRefeicao(refeicaoDe(alimento, porcaoG, day, categoria))
        }
    }

    fun removerRefeicao(r: Refeicao) =
        lancar("Não consegui remover a refeição.") { repo.removerRefeicao(r) }

    val fraseDoDia = hoje
        .map { Frases.doDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Frases.doDia(0L))

    val dicaDescansoAtivo: StateFlow<String> = combine(treinoSugerido, hoje) { t, dia ->
        Frases.descansoAtivo(t.musculosAlvo, dia)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val arenaDisponivel: Boolean get() = Graph.sync.habilitado

    val sessaoGlobal: StateFlow<Sessao?> = Graph.auth.sessao

    private val _authCarregando = MutableStateFlow(false)
    val authCarregando: StateFlow<Boolean> = _authCarregando.asStateFlow()
    val googleDisponivel = Graph.auth.googleDisponivel
    val recuperandoSenha = Graph.auth.recuperandoSenha
    val conversaoPendente = Graph.auth.conversaoPendente
    private val _authMensagem = MutableStateFlow<String?>(null)
    val authMensagem = _authMensagem.asStateFlow()
    private val _criandoPerfil = MutableStateFlow(false)
    val criandoPerfil = _criandoPerfil.asStateFlow()
    val entryDraft = com.liferpg.data.EntryDraft(Graph.appContext)

    fun prepararEntrada() { viewModelScope.launch {
        Graph.auth.protegerSaveLocal()
        Graph.auth.carregarProvedores()
    } }
    fun limparMensagemAuth() { _authMensagem.value = null }

    private val _leaderboard = MutableStateFlow<List<RankingGlobal>>(emptyList())
    val leaderboard: StateFlow<List<RankingGlobal>> = _leaderboard.asStateFlow()
    private val _leaderboardCarregando = MutableStateFlow(false)
    val leaderboardCarregando: StateFlow<Boolean> = _leaderboardCarregando.asStateFlow()
    private val _leaderboardErro = MutableStateFlow<String?>(null)
    val leaderboardErro: StateFlow<String?> = _leaderboardErro.asStateFlow()

    private val _arenaSyncStatus = MutableStateFlow<String?>(null)
    val arenaSyncStatus: StateFlow<String?> = _arenaSyncStatus.asStateFlow()
    private var rankingPublishJob: Job? = null

    private val _amizadesArena = MutableStateFlow<List<AmizadeArena>>(emptyList())
    val amizadesArena: StateFlow<List<AmizadeArena>> = _amizadesArena.asStateFlow()

    private val _perfilAmigo = MutableStateFlow<PerfilAmigo?>(null)
    val perfilAmigo: StateFlow<PerfilAmigo?> = _perfilAmigo.asStateFlow()
    private val _perfilAmigoCarregando = MutableStateFlow(false)
    val perfilAmigoCarregando: StateFlow<Boolean> = _perfilAmigoCarregando.asStateFlow()
    private var perfilAmigoJob: Job? = null

    fun carregarPerfilAmigo(jogadorId: String) {
        perfilAmigoJob?.cancel()
        _perfilAmigo.value = null
        _perfilAmigoCarregando.value = false
        if (_amizadesArena.value.none { it.jogadorId == jogadorId && it.status == "accepted" }) return
        val uid = sessaoGlobal.value?.userId ?: return
        perfilAmigoJob = viewModelScope.launch {
            _perfilAmigoCarregando.value = true
            Graph.sync.verPerfilAmigo(jogadorId)
                .onSuccess { result ->
                    val evolution = Graph.sync.verEvolucao(jogadorId).getOrNull()
                    if (sessaoGlobal.value?.userId == uid) _perfilAmigo.value = result?.copy(evolution = evolution)
                }
                .onFailure { _erros.trySend(it.message ?: "Não consegui abrir o perfil do aliado.") }
            _perfilAmigoCarregando.value = false
        }
    }

    private suspend fun publicarMeuPerfilAmigos() {
        val p = perfil.value ?: return
        val sessions = repo.sessoesRecentes(12).first()
        val catalog = repo.catalogo.exercicios()
        val namesById = catalog.associateBy { it.id }
        val routineList = repo.rotinas.first().take(30).map { routine ->
            RotinaAmigo(
                routine.nome.take(60),
                repo.decodificarItensRotina(routine.itensJson).take(40).mapNotNull { item ->
                    namesById[item.exercicioId]?.nome?.take(55)
                },
                repo.decodificarDiasRotina(routine.itensJson).sorted(),
                repo.decodificarItensRotina(routine.itensJson).take(40),
            )
        }
        val scores = repo.melhoresScores.first().associate { it.exercicioId to it.melhor }
        val ranks = Musculo.entries.mapNotNull { muscle ->
            val rank = RankEngine.ratingMusculo(catalog.filter { it.musculo == muscle }, scores)
            if (rank.patente == Patente.SEM_PATENTE) null else PatenteAmigo(muscle.nomeExibicao, rank.rotulo)
        }
        Graph.sync.publicarPerfilAmigos(PerfilAmigo(
            level = RankEngine.nivelDoXp(p.xpTotal),
            streak = RankEngine.streakVigente(p.streak, p.ultimoDiaTreino, hoje.value),
            bestStreak = p.melhorStreak,
            totalSets = repo.totalSeries.first().coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalSessions = repo.totalSessoes().first(),
            routines = routineList,
            recentWorkouts = sessions.map { TreinoAmigo(it.nome.take(60), it.epochDay, it.series, it.exercicios, it.recordes, it.duracaoSeg, repo.decodificarItensRotina(it.itensJson).take(40)) },
            muscleRanks = ranks,
            identity = IdentidadeAmigo(p.lema, p.biografia, p.classe, p.estiloMoldura, p.focoTreino, p.seloPerfil),
            records = construirRecordes(repo.recordesExercicios.first(), catalog),
        )).getOrThrow()
    }

    private val _desafiosArena = MutableStateFlow<List<DesafioArena>>(emptyList())
    val desafiosArena: StateFlow<List<DesafioArena>> = _desafiosArena.asStateFlow()

    private val _pactos = MutableStateFlow<List<com.liferpg.data.sync.TrainingPact>>(emptyList())
    val pactos = _pactos.asStateFlow()
    private val _pactosLoading = MutableStateFlow(false)
    val pactosLoading = _pactosLoading.asStateFlow()
    private val _pactosError = MutableStateFlow<String?>(null)
    val pactosError = _pactosError.asStateFlow()
    private var pactRefreshJob: Job? = null
    fun carregarPactos() {
        if (pactRefreshJob?.isActive == true) return
        val uid = sessaoGlobal.value?.userId ?: return
        pactRefreshJob = viewModelScope.launch {
            _pactosLoading.value = true; _pactosError.value = null
            try {
                val rows = Graph.sync.pactos().getOrThrow()
                if (sessaoGlobal.value?.userId != uid) return@launch
                _pactos.value = rows
                var wrote = false
                for (pact in rows.filter { it.status == "active" && it.members.any { m -> m.uid == uid && m.status == "accepted" && m.days < m.goal } }) {
                    val started = pact.startedMillis ?: continue
                    val localDays = historicoValido.value.filter { it.timestamp >= started && it.epochDay <= hoje.value }.groupBy { it.epochDay }
                    val cardioDays = cardio.history.value.filter { it.startedAt >= started && it.epochDay <= hoje.value && it.accumulatedMs >= com.liferpg.domain.CardioEngine.STREAK_SECONDS * 1000L }.map { it.epochDay }.toSet()
                    for (day in (localDays.keys + cardioDays).sorted()) {
                        if (sessaoGlobal.value?.userId != uid) return@launch
                        val source = if (localDays[day].orEmpty().any { it.origem == "CAMERA" }) "CAMERA" else "MANUAL"
                        Graph.sync.contribuirPacto(pact.id, day, source).onSuccess { wrote = true }
                            .onFailure { _pactosError.value = "Há contribuições pendentes. Atualize quando tiver conexão. " + it.message.orEmpty() }
                    }
                }
                if (wrote) Graph.sync.pactos().onSuccess { if (sessaoGlobal.value?.userId == uid) _pactos.value = it }
                com.liferpg.notif.ArenaConvites.avisarPactos(Graph.appContext, uid, _pactos.value)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _pactosError.value = e.message ?: "Não consegui carregar os pactos." }
            finally { _pactosLoading.value = false }
        }
    }
    private fun pactAction(onSuccess: () -> Unit = {}, action: suspend () -> Result<Unit>) {
        if (_pactosLoading.value) return
        _pactosLoading.value = true
        viewModelScope.launch {
            try { action().onSuccess { onSuccess() }.onFailure { _erros.trySend(it.message ?: "Não consegui concluir a ação.") } }
            finally { _pactosLoading.value = false; carregarPactos() }
        }
    }
    fun criarPacto(name: String, boss: String, targets: List<String>, goal: Int, onSuccess: () -> Unit = {}) = pactAction(onSuccess) { Graph.sync.criarPacto(name, boss, targets, goal) }
    fun responderPacto(id: String, accept: Boolean, goal: Int) = pactAction { Graph.sync.responderPacto(id, accept, goal) }
    fun iniciarPacto(id: String) = pactAction { Graph.sync.iniciarPacto(id) }
    fun cancelarPacto(id: String) = pactAction { Graph.sync.cancelarPacto(id) }

    private val _cloudBackup = MutableStateFlow<com.liferpg.data.sync.CloudBackup?>(null)
    val cloudBackup = _cloudBackup.asStateFlow()
    private val _cloudBusy = MutableStateFlow(false)
    val cloudBusy = _cloudBusy.asStateFlow()
    private val _cloudMessage = MutableStateFlow<String?>(null)
    val cloudMessage = _cloudMessage.asStateFlow()
    private val _cloudChecked = MutableStateFlow(false)
    val cloudChecked = _cloudChecked.asStateFlow()
    private var cloudBackupOwner: String? = null
    private val restoreStore by lazy { com.liferpg.data.LocalRestoreStore(Graph.appContext.noBackupFilesDir) }
    private val _localRecovery = MutableStateFlow<com.liferpg.data.RestorePoint?>(null)
    val localRecovery = _localRecovery.asStateFlow()
    fun conferirCopiaAnterior() {
        val uid = sessaoGlobal.value?.userId ?: run { _localRecovery.value = null; return }
        viewModelScope.launch {
            val point = withContext(Dispatchers.IO) { restoreStore.read(uid) }
            if (sessaoGlobal.value?.userId == uid) _localRecovery.value = point
        }
    }
    fun conferirBackupNuvem() {
        if (!_cloudBusy.compareAndSet(false, true)) return
        val uid = sessaoGlobal.value?.userId
        viewModelScope.launch {
            try {
                _cloudChecked.value = false; _cloudMessage.value = null
                Graph.sync.lerBackup().onSuccess {
                    if (uid != null && sessaoGlobal.value?.userId == uid) {
                        cloudBackupOwner = uid; _cloudBackup.value = it; _cloudChecked.value = true
                    }
                }.onFailure { _cloudMessage.value = it.message ?: "Não consegui conferir o backup." }
            } finally { _cloudBusy.value = false }
        }
    }
    fun salvarBackupNuvem() {
        val uid = sessaoGlobal.value?.userId ?: return
        if (cloudBackupOwner != uid || !_cloudChecked.value || !_cloudBusy.compareAndSet(false, true)) return
        val revision = _cloudBackup.value?.revision ?: 0L
        viewModelScope.launch {
            try {
                val payload = withContext(Dispatchers.IO) { repo.exportarBackupJson() }
                if (sessaoGlobal.value?.userId != uid) return@launch
                Graph.sync.salvarBackup(payload, revision).onSuccess {
                    if (sessaoGlobal.value?.userId != uid) return@onSuccess
                    _cloudMessage.value = "Backup salvo na sua conta. Fotos corporais não são enviadas."
                    _cloudBackup.value = com.liferpg.data.sync.CloudBackup(it, payload, java.time.OffsetDateTime.now().toString())
                }.onFailure { _cloudChecked.value = false; _cloudMessage.value = it.message }
            } finally { _cloudBusy.value = false }
        }
    }
    fun restaurarBackupNuvem() {
        if (cloudBackupOwner != sessaoGlobal.value?.userId) return
        val backup = _cloudBackup.value ?: return
        restaurarComCopiaAnterior("Progresso da conta restaurado. Suas fotos locais foram preservadas.") { backup.payload }
    }
    fun restaurarCopiaAnterior() {
        val uid = sessaoGlobal.value?.userId ?: return
        restaurarComCopiaAnterior("Cópia anterior recuperada. O save que estava aberto ficou guardado para você poder voltar.") {
            restoreStore.read(uid)?.payload ?: error("A cópia anterior não está disponível.")
        }
    }
    private fun restaurarComCopiaAnterior(success: String, source: suspend () -> String) {
        val uid = sessaoGlobal.value?.userId ?: return
        if (sessao.value?.concluida == false) { avisar("Encerre o treino em andamento antes de restaurar."); return }
        if (!_cloudBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            var restored = false
            try {
                withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    val payload = source()
                    restoreStore.prepare(uid, repo.exportarBackupJson())
                    try {
                        repo.restaurarBackupJson(payload) { sessaoGlobal.value?.userId == uid }
                        restored = true
                        restoreStore.committed() // Candidate remains recoverable if final file promotion fails.
                    } catch (e: Exception) { restoreStore.failed(); throw e }
                }
                if (sessaoGlobal.value?.userId != uid) return@launch
                activeWorkoutStore.save(null); _sessao.value = null
                Graph.auth.protegerSaveLocal()
                _cloudMessage.value = success
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _cloudMessage.value = if (restored) "O progresso foi restaurado, mas a tela não terminou de atualizar. Feche e abra o app. A cópia anterior ficou guardada."
                else "Não consegui restaurar. Seu progresso anterior foi mantido." }
            finally { _cloudBusy.value = false; conferirCopiaAnterior() }
        }
    }
    fun apagarBackupNuvem() {
        val uid = sessaoGlobal.value?.userId ?: return
        if (!_cloudBusy.compareAndSet(false, true)) return
        viewModelScope.launch { try {
            Graph.sync.apagarBackup().onSuccess { if (sessaoGlobal.value?.userId == uid) { _cloudBackup.value = null; _cloudMessage.value = "Cópia da nuvem apagada. Seu progresso local permanece." } }
                .onFailure { _cloudMessage.value = it.message }
        } finally { _cloudBusy.value = false } }
    }

    val convitesPendentes: StateFlow<Int> = combine(_amizadesArena, _desafiosArena, _pactos, sessaoGlobal) { amizades, desafios, pacts, account ->
        amizades.count { it.status == "pending" && !it.enviadaPorMim } +
            desafios.count { it.status == "pending" && !it.euDesafiei } + pacts.count { it.status == "forming" && it.members.any { m -> m.uid == account?.userId && m.status == "pending" } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _socialCarregando = MutableStateFlow(false)
    val socialCarregando: StateFlow<Boolean> = _socialCarregando.asStateFlow()
    private var socialRefreshJob: Job? = null

    private val _cacadoresEncontrados = MutableStateFlow<List<RankingGlobal>>(emptyList())
    val cacadoresEncontrados: StateFlow<List<RankingGlobal>> = _cacadoresEncontrados.asStateFlow()
    private val _buscaCacadoresCarregando = MutableStateFlow(false)
    val buscaCacadoresCarregando: StateFlow<Boolean> = _buscaCacadoresCarregando.asStateFlow()
    private val _buscaCacadoresErro = MutableStateFlow<String?>(null)
    val buscaCacadoresErro: StateFlow<String?> = _buscaCacadoresErro.asStateFlow()
    private var buscaCacadoresJob: Job? = null
    private var buscaCacadoresVersao = 0

    fun entrarEmail(email: String, senha: String) =
        autenticar { Graph.auth.entrarEmail(email.trim(), senha) }

    fun cadastrar(email: String, senha: String) =
        autenticar {
            if (sessaoGlobal.value?.anonimo == true || conversaoPendente.value) Graph.auth.converterConta(email.trim(), senha)
            else Graph.auth.cadastrar(email.trim(), senha)
        }

    fun entrarGoogle(open: (String) -> Unit) {
        if (!_authCarregando.compareAndSet(false, true)) return
        viewModelScope.launch { try {
            _authMensagem.value = null
            Graph.auth.iniciarGoogle().onSuccess(open).onFailure { _authMensagem.value = it.message }
        } finally { _authCarregando.value = false } }
    }
    fun recuperarSenha(email: String) {
        if (!_authCarregando.compareAndSet(false, true)) return
        viewModelScope.launch { try {
            Graph.auth.recuperarSenha(email.trim())
                .onSuccess { _authMensagem.value = "Se houver uma conta com esse e-mail, você receberá um link para redefinir sua senha. Abra neste aparelho." }
                .onFailure { _authMensagem.value = it.message }
        } finally { _authCarregando.value = false } }
    }
    fun concluirLinkAuth(uri: android.net.Uri) {
        if (uri.scheme != "remnant" || uri.host != "auth") return
        viewModelScope.launch {
            Graph.auth.concluirLink(uri).onFailure { _authMensagem.value = it.message }
                .onSuccess { _authMensagem.value = "Confirmação recebida. Finalize sua conta para continuar." }
        }
    }
    fun salvarNovaSenha(senha: String) = autenticar { Graph.auth.salvarNovaSenha(senha) }
    fun cancelarRecuperacao() = Graph.auth.cancelarRecuperacao()

    private fun autenticar(acao: suspend () -> Result<Sessao>) {
        if (!_authCarregando.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
            _authMensagem.value = null
            acao()
                .onSuccess {
                    carregarLeaderboard()
                    carregarSocial()
                }
                .onFailure { _authMensagem.value = it.message ?: "Falha de autenticação." }
            } finally { _authCarregando.value = false }
        }
    }

    fun sairDaConta() {
        socialRefreshJob?.cancel()
        rankingPublishJob?.cancel()
        _arenaSyncStatus.value = null
        _leaderboard.value = emptyList()
        _amizadesArena.value = emptyList()
        perfilAmigoJob?.cancel()
        _perfilAmigo.value = null
        _desafiosArena.value = emptyList()
        Notificacoes.limparConvitesArena(Graph.appContext)
        buscaCacadoresJob?.cancel()
        buscaCacadoresVersao++
        _cacadoresEncontrados.value = emptyList()
        _buscaCacadoresCarregando.value = false
        _buscaCacadoresErro.value = null
        viewModelScope.launch { Graph.auth.sair() }
    }

    fun apagarDadosRemotos() {
        if (!Graph.sync.habilitado) {
            _erros.trySend("A Arena Global não está configurada nesta versão.")
            return
        }
        viewModelScope.launch {
            Graph.sync.apagarMeusDados()
                .onSuccess {
                    _leaderboard.value = emptyList()
                    Graph.auth.sair()
                    _erros.trySend("Seus dados da Arena foram apagados.")
                }
                .onFailure { _erros.trySend(it.message ?: "Não consegui apagar os dados da Arena.") }
        }
    }

    fun carregarLeaderboard() {
        if (!Graph.sync.habilitado) return
        viewModelScope.launch {
            _leaderboardCarregando.value = true
            _leaderboardErro.value = null
            try {
                Graph.sync.leaderboard(resumoCompetitivo.value.temporada)
                    .onSuccess { _leaderboard.value = it }
                    .onFailure { _leaderboardErro.value = "Não foi possível carregar a classificação. Tente novamente." }
            } finally {
                _leaderboardCarregando.value = false
            }
        }
    }

    fun publicarRankingAgora(avisar: Boolean = false) {
        if (sessaoGlobal.value?.vinculada != true || conversaoPendente.value) return
        if (!Graph.sync.habilitado) return
        rankingPublishJob?.cancel()
        rankingPublishJob = viewModelScope.launch {
            val profile = perfil.value
            if (profile == null) {
                _arenaSyncStatus.value = "Crie seu perfil para aparecer na Arena."
                return@launch
            }
            val nickname = profile.nome.ifBlank { "Caçador" }
            _arenaSyncStatus.value = "Sincronizando seu perfil..."
            val r = resumoCompetitivo.first { it.temporada == Temporadas.numeroDaTemporada(hoje.value) }
            if (!avisar) {
                val remote = Graph.sync.meuRanking().getOrElse {
                    _arenaSyncStatus.value = it.message ?: "Não consegui conferir esta conta. Toque em Atualizar."
                    return@launch
                }
                if (remote?.protegePublicacaoLocal(nickname, r.powerScore) == true) {
                    _arenaSyncStatus.value = "Esta conta já publica ${remote.apelido}. " +
                        "Para publicar $nickname como outro jogador, entre com outra conta neste aparelho. " +
                        "Atualizar substitui o perfil publicado."
                    return@launch
                }
            }
            Graph.sync.publicarRanking(
                RankingPublicacao(
                    apelido = nickname,
                    powerScore = r.powerScore,
                    patente = r.patente.ordinal,
                    divisao = r.divisao?.ordinal,
                    lp = r.lp,
                    temporada = r.temporada,
                ),
            ).onSuccess {
                _arenaSyncStatus.value = "Perfil publicado na temporada ${r.temporada}."
                runCatching { publicarMeuPerfilAmigos() }.onFailure {
                    _arenaSyncStatus.value = "Power publicado. Os detalhes para aliados aguardam sincronização; toque em Atualizar para tentar novamente."
                }
                val review = revisaoSemanal.value
                val evolution = org.json.JSONObject().put("week", review.firstDay).put("goal", review.goal).put("days", review.trainedDays)
                    .put("records", org.json.JSONArray().apply { review.improvements.take(1000).forEach { (id, pct) ->
                        put(org.json.JSONObject().put("id", id).put("pct", pct.coerceAtMost(10000f).toDouble()))
                    } })
                jornadas.value.firstOrNull { !it.campaign.archived && !it.finished }?.let { journey ->
                    evolution.put("campaign", org.json.JSONObject().put("name", journey.campaign.name).put("chapters", journey.victories).put("weeks", journey.campaign.weeks))
                }
                Graph.sync.publicarEvolucao(evolution.toString())
                if (avisar) _erros.trySend("Patente e Power publicados na Arena. Seus treinos continuam neste aparelho.")
                Graph.sync.leaderboard(r.temporada).onSuccess { _leaderboard.value = it }
            }.onFailure {
                _arenaSyncStatus.value = it.message ?: "Não consegui publicar seu perfil. Toque em Atualizar."
                if (avisar) _erros.trySend(it.message ?: "Não consegui publicar seu perfil.")
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(sessaoGlobal, historicoValido) { account, sets -> account?.userId to sets.size }
                .distinctUntilChanged().debounce(1600).collect { (uid, _) ->
                    _localRecovery.value = null
                    if (uid != null) { carregarPactos(); conferirCopiaAnterior() } else { _pactos.value = emptyList(); _cloudBackup.value = null; _cloudChecked.value = false }
                }
        }
        viewModelScope.launch {
            val training = combine(perfil, resumoCompetitivo, repo.rotinas, meusRecordes) { profile, rank, routines, records ->
                listOf(profile, rank, routines, records)
            }
            combine(sessaoGlobal, training, revisaoSemanal, jornadas) { session, data, review, journeys ->
                val profile = data[0] as? Perfil
                val rank = data[1] as ResumoCompetitivo
                if (session == null || !session.vinculada || profile == null ||
                    rank.temporada != Temporadas.numeroDaTemporada(hoje.value)
                ) null else listOf(session.userId, data, review, journeys)
            }.distinctUntilChanged().debounce(1200).collect { publication ->
                if (publication != null) publicarRankingAgora()
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(resumoCompetitivo, hoje) { r, dia -> r to dia }
                .distinctUntilChanged { (rOld, diaOld), (rNew, diaNew) ->
                    diaOld == diaNew &&
                        rOld.powerScore.roundToInt() == rNew.powerScore.roundToInt()
                }
                .collect { (r, dia) ->
                    if (r.powerScore > 0f) {
                        runCatching {
                            repo.registrarCompetitivo(
                                dia,
                                r.temporada,
                                r.powerScore,
                                r.patente.ordinal,
                                r.lp,
                            )
                        }
                    }
                }
        }
    }

    val volumePorMusculo: StateFlow<Map<Musculo, Int>> = repo.volumePorMusculo
        .map { lista ->
            lista.mapNotNull {
                runCatching { Musculo.valueOf(it.musculo) to it.series }.getOrNull()
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val missoesHoje = hoje
        .flatMapLatest { repo.missoesDoDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missoesSemana = hoje.flatMapLatest(repo::missoesSemana)
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val historicoMissoes = hoje.flatMapLatest(repo::historicoMissoes)
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val podeAdaptarMissoes = hoje.flatMapLatest(repo::podeAdaptarMissoes)
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),false)
    fun adaptarMissoes(mode: com.liferpg.domain.SystemMode) = lancar("Não consegui adaptar suas ordens.") {
        repo.adaptarMissoes(hoje.value,mode)
    }

    val seriesHoje = hoje
        .flatMapLatest { repo.seriesDoDia(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ultimasSeries = repo.ultimasSeries(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conquistas: StateFlow<Set<String>> = repo.conquistas
        .map { lista -> lista.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val totalSeries: StateFlow<Long> = repo.totalSeries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val missoesConcluidasTotal: StateFlow<Int> = repo.totalMissoesConcluidas
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val conquistasCatalogo: StateFlow<List<ConquistaDef>> = exercicios
        .map { ConquistaEngine.gerar(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val estadoConquistas: StateFlow<EstadoConquistas> = combine(
        perfil,
        resumoCompetitivo,
        totalSeries,
        volumePorMusculo,
        nutricaoHoje,
    ) { perf, resumo, total, volMap, nutri ->
        construirEstadoConquistas(perf, resumo, total, volMap, nutri.proteinaG)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EstadoConquistas())

    val trofeusGanhos = estadoConquistas
        .map { TrofeuEngine.ganhos(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val ecoPassado: StateFlow<EcoPassado> = combine(resumoCompetitivo, snapshots, hoje) { r, snaps, dia ->
        val ref = snaps.filter { (dia - it.epochDay) in 7..400 }.minByOrNull { it.epochDay }
        EcoPassado(
            r.powerScore,
            ref?.powerScore ?: 0f,
            if (ref != null) (dia - ref.epochDay).toInt() else 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EcoPassado(0f, 0f, 0))

    val titulosGanhos = combine(estadoConquistas, jornadas) { state, journeys ->
        TituloEngine.ganhos(state) + if (journeys.any { it.conquered }) listOf(com.liferpg.domain.TituloDef(
            "legado_forja", "Forjado na Jornada", "Conquiste todos os capítulos de uma campanha pessoal", com.liferpg.domain.Condicao.NivelMin(1))) else emptyList()
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tituloEquipado = combine(prefsEfetivas, titulosGanhos) { prefs, ganhos ->
        ganhos.firstOrNull { it.id == prefs.tituloEquipado } ?: ganhos.lastOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun equiparTitulo(id: String) = lancar("Não consegui equipar o título.") {
        repo.salvarPreferencias(prefsEfetivas.value.copy(tituloEquipado = id))
    }

    private val _conquistasDesbloqueadas = Channel<List<ConquistaDef>>(Channel.BUFFERED)
    val conquistasDesbloqueadas = _conquistasDesbloqueadas.receiveAsFlow()

    private val _promocaoFirmamento = Channel<EstagioFirmamento>(Channel.BUFFERED)
    val promocaoFirmamento = _promocaoFirmamento.receiveAsFlow()

    init {
        viewModelScope.launch {
            perfil.collect { p ->
                if (p == null) return@collect
                val atual = EstagioFirmamento.deNivel(RankEngine.nivelDoXp(p.xpTotal))
                val ant = estagioFirmamentoAnterior
                if (ant == null) {
                    estagioFirmamentoAnterior = atual
                } else if (atual.ordinal > ant.ordinal) {
                    _promocaoFirmamento.trySend(atual)
                    estagioFirmamentoAnterior = atual
                } else if (atual.ordinal < ant.ordinal) {
                    estagioFirmamentoAnterior = atual
                }
            }
        }
    }

    fun construirEstadoConquistas(
        perf: Perfil?,
        resumo: ResumoCompetitivo,
        total: Long,
        volMap: Map<Musculo, Int>,
        proteinaHojeG: Float,
    ): EstadoConquistas {
        val catalogo = exercicios.value
        val scores = melhoresScores.value
        val registrados = scores.keys.toSet()
        val patExerc = HashMap<String, Patente>(catalogo.size)
        for (e in catalogo) {
            e.benchmarks?.let { patExerc[e.id] = RankEngine.patenteDoScore(scores[e.id] ?: 0f, it) }
        }
        val porMusculo = catalogo.groupBy { it.musculo }
        val cobertura = Musculo.entries.associateWith { m ->
            val lista = porMusculo[m] ?: emptyList()
            lista.isNotEmpty() && lista.all { it.id in registrados }
        }
        val encerradas = temporadasEncerradas.value
        return EstadoConquistas(
            total,
            maxOf(perf?.streak ?: 0, perf?.melhorStreak ?: 0),
            RankEngine.nivelDoXp(perf?.xpTotal ?: 0L),
            resumo.powerScore,
            resumo.percentilTopo,
            resumo.patente,
            resumo.divisao,
            resumo.ratings.mapValues { it.value.patente },
            volMap,
            patExerc,
            registrados,
            cobertura,
            missoesConcluidasTotal.value,
            encerradas.size,
            encerradas.maxOfOrNull {
                Patente.entries[it.patentePico.coerceIn(0, Patente.entries.size - 1)]
            } ?: Patente.SEM_PATENTE,
            metaProteina.value > 0 && proteinaHojeG >= metaProteina.value,
        )
    }

    init {
        viewModelScope.launch {
            combine(estadoConquistas, conquistas) { estado, ja -> estado to ja }
                .collect { (estado, ja) ->
                    val catalogo = conquistasCatalogo.value
                    if (catalogo.isEmpty()) return@collect
                    val novas = ConquistaEngine.avaliar(catalogo, estado).filter { it !in ja }
                    if (novas.isNotEmpty()) {
                        val confirmadas =
                            runCatching { repo.desbloquearConquistas(novas) }.getOrDefault(emptyList())
                        if (confirmadas.isNotEmpty()) {
                            val porId = catalogo.associateBy { it.id }
                            _conquistasDesbloqueadas.trySend(confirmadas.mapNotNull { porId[it] })
                        }
                    }
                }
        }
    }

    fun seriesDoExercicio(id: String) = repo.seriesDoExercicio(id)

    val eventos = _eventos.receiveAsFlow()

    private val _erros = Channel<String>(Channel.BUFFERED)
    val erros = _erros.receiveAsFlow()

    private fun lancar(msgErro: String, acao: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { acao() }
                .onFailure {
                    Log.e("MainViewModel", msgErro, it)
                    _erros.trySend((it as? IllegalArgumentException)?.message ?: msgErro)
                }
        }
    }

    private val _descansoFimEm = MutableStateFlow<Long?>(null)

    private val _descansoDuracao = MutableStateFlow(90)
    val descansoDuracao: StateFlow<Int> = _descansoDuracao.asStateFlow()

    val descansoRestante: StateFlow<Int?> = flow {
        while (true) {
            val fim = _descansoFimEm.value
            if (fim == null) {
                emit(null)
                _descansoFimEm.first { it != null }
            } else {
                val restante = fim - System.currentTimeMillis()
                if (restante <= 0) {
                    emit(0)
                    delay(1500)
                    if (_descansoFimEm.value == fim) {
                        _descansoFimEm.value = null
                        activeWorkoutStore.saveRest(null, _descansoDuracao.value)
                    }
                } else {
                    emit(((restante + 999) / 1000).toInt())
                    delay(250)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = exercicios.first { it.isNotEmpty() }
            activeWorkoutStore.restore(catalog)?.let { restored ->
                _sessao.value = restored
            }
            activeWorkoutStore.restoreRest()?.let { (end, duration) ->
                _descansoDuracao.value = duration
                _descansoFimEm.value = end
                agendarNotificacaoDescanso(end)
            }
        }
    }

    fun carregarSocial(silencioso: Boolean = false) {
        val uid = sessaoGlobal.value?.userId
        if (!Graph.sync.habilitado || uid == null || _socialCarregando.value || socialRefreshJob?.isActive == true) return
        socialRefreshJob = viewModelScope.launch {
            if (!silencioso) _socialCarregando.value = true
            try {
                val amizades = Graph.sync.amizades().onFailure {
                    if (!silencioso) _erros.trySend(it.message ?: "Não consegui carregar suas conexões.")
                }.getOrNull()
                val desafios = Graph.sync.desafios().onFailure {
                    if (!silencioso) _erros.trySend(it.message ?: "Não consegui carregar seus duelos.")
                }.getOrNull()
                if (sessaoGlobal.value?.userId != uid) return@launch
                if (amizades != null) _amizadesArena.value = amizades
                if (desafios != null) _desafiosArena.value = desafios
                if (amizades != null && desafios != null) {
                    com.liferpg.notif.ArenaConvites.avisar(Graph.appContext, uid, amizades, desafios)
                }
            } finally {
                if (!silencioso) _socialCarregando.value = false
            }
        }
    }

    fun buscarCacadores(consulta: String) {
        val termo = consulta.trim().take(32)
        buscaCacadoresJob?.cancel()
        val versao = ++buscaCacadoresVersao
        _cacadoresEncontrados.value = emptyList()
        _buscaCacadoresErro.value = null
        if (termo.length < 2 || sessaoGlobal.value == null) {
            _buscaCacadoresCarregando.value = false
            return
        }
        _buscaCacadoresCarregando.value = true
        buscaCacadoresJob = viewModelScope.launch {
            delay(350)
            Graph.sync.buscarCacadores(termo)
                .onSuccess { if (versao == buscaCacadoresVersao) _cacadoresEncontrados.value = it }
                .onFailure { if (versao == buscaCacadoresVersao) _buscaCacadoresErro.value = it.message ?: "Busca indisponível agora." }
            if (versao == buscaCacadoresVersao) _buscaCacadoresCarregando.value = false
        }
    }

    fun solicitarAmizade(jogadorId: String) = acaoSocial("Pedido de amizade enviado.") {
        Graph.sync.solicitarAmizade(jogadorId)
    }

    fun responderAmizade(pedidoId: String, aceitar: Boolean) =
        acaoSocial(if (aceitar) "Agora vocês são aliados." else "Pedido recusado.") {
            Graph.sync.responderAmizade(pedidoId, aceitar)
        }

    fun removerAmizade(jogadorId: String) = acaoSocial("Conexão removida.") {
        Graph.sync.removerAmizade(jogadorId)
    }

    fun criarDesafio(jogadorId: String, duracaoHoras: Int) =
        acaoSocial(if (duracaoHoras == 24) "Duelo de 24 horas enviado." else "Duelo de 7 dias enviado.") {
            Graph.sync.criarDesafio(jogadorId, duracaoHoras, if (duracaoHoras == 24) 40f else 100f)
        }

    fun responderDesafio(desafioId: String, aceitar: Boolean) =
        acaoSocial(if (aceitar) "Duelo aceito. A ascensão começou." else "Desafio recusado.") {
            Graph.sync.responderDesafio(desafioId, aceitar)
        }

    fun cancelarDesafio(desafioId: String) = acaoSocial("Desafio cancelado.") {
        Graph.sync.cancelarDesafio(desafioId)
    }

    private fun acaoSocial(mensagem: String, acao: suspend () -> Result<Unit>) {
        if (_socialCarregando.value) return
        socialRefreshJob?.cancel()
        viewModelScope.launch {
            _socialCarregando.value = true
            acao()
                .onSuccess {
                    _erros.trySend(mensagem)
                    Graph.sync.amizades().onSuccess { _amizadesArena.value = it }
                    Graph.sync.desafios().onSuccess { _desafiosArena.value = it }
                    sessaoGlobal.value?.userId?.let { uid ->
                        com.liferpg.notif.ArenaConvites.avisar(Graph.appContext, uid, _amizadesArena.value, _desafiosArena.value)
                    }
                }
                .onFailure { _erros.trySend(it.message ?: "A Arena não concluiu esta ação.") }
            _socialCarregando.value = false
        }
    }

    fun iniciarDescanso(segundos: Int = _descansoDuracao.value) {
        val duracao = segundos.coerceIn(15, 600)
        _descansoDuracao.value = duracao
        val fim = System.currentTimeMillis() + duracao * 1000L
        _descansoFimEm.value = fim
        activeWorkoutStore.saveRest(fim, duracao)
        agendarNotificacaoDescanso(fim)
    }

    fun ajustarDescanso(deltaSegundos: Int) {
        val fim = _descansoFimEm.value ?: return
        val agora = System.currentTimeMillis()
        val novoFim = (fim + deltaSegundos * 1000L).coerceIn(agora + 1_000L, agora + 600_000L)
        _descansoFimEm.value = novoFim
        val restanteSeg = ((novoFim - System.currentTimeMillis() + 999) / 1000).toInt()
        if (restanteSeg > _descansoDuracao.value) _descansoDuracao.value = restanteSeg
        activeWorkoutStore.saveRest(novoFim, _descansoDuracao.value)
        agendarNotificacaoDescanso(novoFim)
    }

    fun cancelarDescanso() {
        _descansoFimEm.value = null
        activeWorkoutStore.saveRest(null, _descansoDuracao.value)
        runCatching { Notificacoes.cancelarDescanso(Graph.appContext) }
    }

    private fun agendarNotificacaoDescanso(fimEpochMs: Long) {
        runCatching { Notificacoes.agendarFimDescanso(Graph.appContext, fimEpochMs) }
    }

    private val _registrando = MutableStateFlow(false)
    val registrando: StateFlow<Boolean> = _registrando.asStateFlow()

    private var estagioFirmamentoAnterior: EstagioFirmamento? = null

    fun finalizarEntrada(a: com.liferpg.data.EntryAnswers) {
        if (!_criandoPerfil.compareAndSet(false, true)) return
        viewModelScope.launch { try {
        require(sessaoGlobal.value?.vinculada == true && !conversaoPendente.value) { "Crie uma conta ou entre antes de continuar." }
        require(a.completa()) { "Confira seu nome e suas medidas para continuar." }
        val objetivo = Objetivo.seguro(a.objetivo)
        val classe = com.liferpg.domain.StarterPlan.classe(a.musculo)
        val perfil = Perfil(
            nome = a.nome.trim(),
            classe = classe.name,
            pesoCorporalKg = requireNotNull(a.pesoKg),
            sexo = Sexo.OUTRO.name,
            idade = requireNotNull(a.idadeAnos),
            alturaCm = requireNotNull(a.alturaCm),
            focoTreino = a.musculo?.name ?: "EQUILIBRIO",
            criadoEm = System.currentTimeMillis(),
        )
        val prefs = Preferencias(
            objetivo = objetivo.name,
            experiencia = a.experiencia,
            localPreferido = a.local,
            diasPorSemana = a.dias.size,
            descansoPadraoSeg = objetivo.descansoSeg,
            metaProteinaG = 0,
            rotinaAtiva = null,
            lembretesAtivos = true,
            condicoes = "",
            horarioTreino = HorarioTreino.QUALQUER.name,
            metaCalorias = 0,
        )
        val metas = NutricaoEngine.calcular(perfil, prefs)
        val prefs2 = prefs.copy(metaProteinaG = metas.proteinaG, metaCalorias = 0)
        repo.criarPerfil(perfil, prefs2, a.dias, a.musculo)
        Graph.auth.protegerSaveLocal()
        entryDraft.clear()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { _authMensagem.value = e.message ?: "Não consegui criar seu perfil. Tente novamente." }
        finally { _criandoPerfil.value = false }
        }
    }

    fun registrarSerie(exercicio: Exercicio, pesoKg: Float?, reps: Int?, segundos: Int?, autoRestSeconds: Int? = null, esforco: String = "") {
        if (!_registrando.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                runCatching { repo.registrarSerie(exercicio, pesoKg, reps, segundos, esforco = esforco, equipamento = equipamentoDo(exercicio.id).label) }
                    .onSuccess { reg ->
                        when (reg) {
                            is RegistroSerie.Ok -> {
                                _eventos.send(reg.resultado)
                                acumularNaSessao(exercicio, reg.resultado, pesoKg, reps, segundos, autoRest = autoRestSeconds == null && !reg.suspeito)
                                if(!reg.suspeito && autoRestSeconds != null && autoRestSeconds > 0) iniciarDescanso(autoRestSeconds.coerceIn(15,600))
                                if (reg.suspeito) {
                                    _erros.trySend(
                                        "⚠️ Série salva, mas marcada como suspeita — não conta no ranking.",
                                    )
                                }
                            }
                            is RegistroSerie.Bloqueado -> _erros.trySend("🚫 " + reg.motivo)
                            null -> {}
                        }
                    }
                    .onFailure {
                        Log.e("MainViewModel", "Falha ao registrar série", it)
                        _erros.trySend("Não consegui salvar a série. Tente de novo.")
                    }
            } finally {
                _registrando.value = false
            }
        }
    }

    fun atualizarPeso(pesoKg: Float) =
        lancar("Não consegui atualizar seu peso.") { repo.atualizarPeso(pesoKg) }

    fun registrarPesoCorporal(pesoKg: Float) =
        lancar("Não consegui registrar seu peso.") { repo.registrarPesoCorporal(pesoKg) }

    fun atualizarIdentidade(nome: String, lema: String, classe: String, estilo: String, biografia: String, foco: String, selo: String) =
        lancar("Não consegui salvar seu perfil.") { repo.atualizarIdentidade(nome, lema, classe, estilo, biografia, foco, selo) }

    fun resetTotal() {
        cancelarDescanso()
        lancar("Não consegui resetar os dados.") {
            repo.resetTotal()
            withContext(Dispatchers.IO) { restoreStore.clear() }
            _localRecovery.value = null
        }
    }

    suspend fun gerarBackupJson(): String = repo.exportarBackupJson()

    suspend fun restaurarBackupJson(json: String): String {
        val result = repo.restaurarBackupJson(json)
        return "Backup restaurado: ${result.series} séries, ${result.sessoes} treinos e ${result.rotinas} rotinas."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { MainViewModel(Graph.repository) }
        }
    }
}
