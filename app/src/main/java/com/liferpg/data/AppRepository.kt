package com.liferpg.data

import androidx.room.withTransaction
import com.liferpg.domain.ClassePersonagem
import com.liferpg.domain.BossCampaign
import com.liferpg.domain.CameraBoss
import com.liferpg.domain.CameraTrialState
import com.liferpg.domain.CameraTrials
import com.liferpg.domain.Exercicio
import com.liferpg.domain.ItemRotinaDef
import com.liferpg.domain.MissaoEngine
import com.liferpg.domain.Patente
import com.liferpg.domain.RecordePessoal
import com.liferpg.domain.MarcaPessoal
import com.liferpg.domain.RankEngine
import com.liferpg.domain.RotinaItens
import com.liferpg.domain.ValidadorTreino
import com.liferpg.domain.Veredito
import com.squareup.moshi.Moshi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max

/**
 * The single write path into the database. Every mutation runs on [NonCancellable] so a
 * screen leaving composition can never abort a half-written transaction.
 */
class AppRepository(private val db: AppDatabase, val catalogo: CatalogoRepo, private val routes: CardioRouteStore? = null) {

    data class BackupRestoreResult(val series: Int, val sessoes: Int, val rotinas: Int, val refeicoes: Int)

    private val dao: AppDao = db.dao()

    val perfil: Flow<Perfil?> = dao.perfilFlow()
    val melhoresScores: Flow<List<MelhorScore>> = dao.melhoresScores()
    val recordesExercicios: Flow<List<RecordeExercicio>> = dao.recordesExercicios()
    val volumePorMusculo: Flow<List<VolumeMusculo>> = dao.volumePorMusculo()
    val conquistas: Flow<List<ConquistaDesbloqueada>> = dao.conquistasFlow()
    val totalSeries: Flow<Long> = dao.totalSeriesFlow()
    val totalMissoesConcluidas: Flow<Int> = dao.totalMissoesConcluidasFlow()
    val ultimoTreinoPorMusculo: Flow<List<UltimoTreinoMusculo>> = dao.ultimoTreinoPorMusculo()
    val temporadasEncerradas: Flow<List<TemporadaResumo>> = dao.temporadasEncerradas()
    val preferencias: Flow<Preferencias?> = dao.preferenciasFlow()
    val rotinas: Flow<List<RotinaCustom>> = dao.rotinasFlow()
    val cameraBosses: Flow<List<CameraBossProgress>> = dao.cameraBossesFlow()
    val validHistory = dao.validHistory()
    val evolutionEntries = dao.evolutionFlow()
    val cardioHistory = dao.cardioHistory()
    val activeCardio = dao.activeCardioFlow()

    suspend fun startCardio(activity: com.liferpg.domain.CardioActivity, goal: com.liferpg.domain.CardioGoal,
        target: Double, clock: com.liferpg.domain.CardioClock, gps: Boolean = false) = withContext(NonCancellable) {
        db.withTransaction {
            check(dao.perfil() != null) { "Entre na sua conta para registrar cardio." }
            check(dao.activeCardio() == null) { "Retome ou encerre seu cardio atual." }
            require(!gps || activity in listOf(com.liferpg.domain.CardioActivity.RUN, com.liferpg.domain.CardioActivity.WALK, com.liferpg.domain.CardioActivity.BIKE))
            dao.saveCardio(com.liferpg.domain.CardioEngine.start(activity, goal, target, clock).let { if (gps) it.copy(source = "GPS") else it })
        }
    }

    suspend fun updateCardio(id: String, clock: com.liferpg.domain.CardioClock, paused: Boolean? = null) = withContext(NonCancellable) {
        db.withTransaction {
            val old = dao.activeCardio()?.takeIf { it.id == id } ?: return@withTransaction
            val next = when (paused) {
                true -> com.liferpg.domain.CardioEngine.pause(old, clock)
                false -> com.liferpg.domain.CardioEngine.resume(old, clock)
                null -> com.liferpg.domain.CardioEngine.checkpoint(old, clock)
            }
            dao.saveCardio(next.copy(status = if (next.accumulatedMs >= com.liferpg.domain.CardioEngine.MAX_MS) "PAUSED" else next.status))
        }
    }
    suspend fun discardCardio(id: String) = withContext(NonCancellable) {
        db.withTransaction { if (dao.activeCardio()?.id == id) { routes?.delete(id); dao.discardCardio(id) } }
    }
    suspend fun freezeInterruptedCardio(id: String, clock: com.liferpg.domain.CardioClock) = withContext(NonCancellable) {
        db.withTransaction {
            val row = dao.activeCardio()?.takeIf { it.id == id && it.source == "GPS" && it.status == "RUNNING" } ?: return@withTransaction
            // No tracker was alive during this gap. Preserve the last persisted active duration.
            dao.saveCardio(row.copy(status = "PAUSED", anchorMs = clock.elapsed, boot = clock.boot, device = clock.device))
        }
    }

    /** The same transaction guards route callbacks against pause, finish, reset and account changes. */
    suspend fun recordCardioTracking(id: String, clock: com.liferpg.domain.CardioClock,
        change: (com.liferpg.domain.CardioRouteState) -> com.liferpg.domain.CardioRouteState): Boolean = withContext(NonCancellable) {
        db.withTransaction {
            val row = dao.activeCardio()?.takeIf { it.id == id && it.status == "RUNNING" && it.source == "GPS" && it.boot == clock.boot && it.device == clock.device } ?: return@withTransaction false
            val store = routes ?: return@withTransaction false
            if (com.liferpg.domain.CardioEngine.elapsed(row, clock) >= com.liferpg.domain.CardioEngine.MAX_MS) {
                dao.saveCardio(com.liferpg.domain.CardioEngine.pause(row, clock)); return@withTransaction false
            }
            val stored = store.read(id)
            // Android can transfer Room but precise routes intentionally do not travel.
            val old = if (stored.points.isEmpty() && stored.meters == 0.0 && row.distanceKm != null)
                stored.copy(meters = row.distanceKm * 1000, steps = row.steps) else stored
            val next = change(old)
            if (next != old) store.write(id, next)
            dao.saveCardio(row.copy(distanceKm = next.meters.takeIf { it >= 10 }?.div(1000), steps = next.steps))
            true
        }
    }

    /** UUID, cardio record, XP and streak commit together. Retrying cannot award twice. */
    suspend fun finishCardio(id: String, clock: com.liferpg.domain.CardioClock, distance: Double?, effort: String,
        note: String, manual: CardioSession? = null): ResultadoSerie? = withContext(NonCancellable) {
        db.withTransaction {
            if (dao.cardioById(id)?.status == "FINISHED") return@withTransaction null
            val old = if (manual != null) {
                check(dao.activeCardio() == null) { "Encerre seu cardio atual antes de registrar outra sessão." }
                require(manual.id == id && manual.source == "MANUAL" && manual.status == "PAUSED")
                manual
            } else dao.activeCardio()?.takeIf { it.id == id } ?: error("Esta sessão não está mais em andamento.")
            val p = dao.perfil() ?: error("Seu perfil não está disponível.")
            val day = java.time.Instant.ofEpochMilli(clock.wall).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            val millis = com.liferpg.domain.CardioEngine.elapsed(old, clock)
            require(millis in 60_000..com.liferpg.domain.CardioEngine.MAX_MS) { "Registre pelo menos um minuto de atividade." }
            val xp = com.liferpg.domain.CardioEngine.reward((millis / 1000).toInt(), dao.cardioXp(day))
            val completed = old.copy(status = "FINISHED", accumulatedMs = millis, anchorMs = 0L, distanceKm = if (old.source == "GPS") old.distanceKm else distance,
                effort = effort, note = note.trim(), endedAt = maxOf(old.startedAt, clock.wall), epochDay = day, xpGained = xp)
            com.liferpg.domain.CardioEngine.validate(completed)
            val qualifies = millis >= com.liferpg.domain.CardioEngine.STREAK_SECONDS * 1000L
            val streak = if (qualifies) RankEngine.novoStreak(p.streak, p.ultimoDiaTreino, day) else p.streak
            dao.saveCardio(completed)
            val missions = atualizarMissoesInterno(day)
            val earned = xp + missions.xp
            dao.salvarPerfil(p.copy(xpTotal = p.xpTotal + earned, streak = streak, melhorStreak = maxOf(p.melhorStreak, streak),
                ultimoDiaTreino = if (qualifies) maxOf(p.ultimoDiaTreino, day) else p.ultimoDiaTreino))
            ResultadoSerie(earned, false, Patente.SEM_PATENTE, Patente.SEM_PATENTE, RankEngine.nivelDoXp(p.xpTotal),
                RankEngine.nivelDoXp(p.xpTotal + earned), 0f, missions.completed, emptyList(), streak,
                qualifies && p.ultimoDiaTreino != day, tipoRegistro = "CARDIO")
        }
    }
    suspend fun saveEvolution(row: EvolutionEntry) = withContext(NonCancellable) {
        com.liferpg.domain.EvolutionCodec.validate(row)
        dao.saveEvolution(row)
    }
    suspend fun startCampaign(name: String, day: Long, weeks: Int, goal: Int) = withContext(NonCancellable) {
        db.withTransaction {
            val existing = dao.evolutionEntries().filter { it.id.startsWith("campaign:") }.map { com.liferpg.domain.EvolutionCodec.campaign(it) }
            require(existing.none { !it.archived && day <= it.endDay }) { "Encerre sua jornada atual antes de começar outra." }
            val campaign = com.liferpg.domain.EvolutionEngine.start(name, day, System.currentTimeMillis(), weeks, goal, dao.todasSeries())
            dao.saveEvolution(com.liferpg.domain.EvolutionCodec.encodeCampaign(campaign))
        }
    }

    suspend fun repeticoesCameraPendentes(boss: CameraBoss): Int = dao.cameraBoss(boss.name)?.pendingReps ?: 0

    /** Atomic checkpoint: attempts reset, but valid workout reps and earned victories never do. */
    suspend fun registrarProvaCamera(boss: CameraBoss, sessionId: String, state: CameraTrialState) = withContext(NonCancellable) {
        db.withTransaction {
            val old = dao.cameraBoss(boss.name) ?: CameraBossProgress(boss.name)
            val same = old.lastSessionId == sessionId
            if (!same && dao.cameraSet("$sessionId:0") != null) return@withTransaction
            if (same && state.revision <= old.lastSessionRevision) return@withTransaction
            val previousReps = if (same) old.lastSessionReps else 0
            if (state.totalReps < previousReps) return@withTransaction
            val won = (state.victories - if (same) old.lastSessionWins else 0).coerceAtLeast(0)
            val died = (state.deaths - if (same) old.lastSessionDeaths else 0).coerceAtLeast(0)
            require(state.completedSets.sum() + state.attemptReps == state.totalReps) { "Séries da câmera inconsistentes." }
            val now = System.currentTimeMillis()
            if (!same) dao.cameraSetsPendentes(boss.name).filter { !it.closed }.forEach {
                dao.salvarCameraSet(it.copy(closed = true, completedAt = now))
            }
            suspend fun checkpointSet(index: Int, reps: Int, closed: Boolean) {
                if (reps <= 0) return
                val id = "$sessionId:$index"
                val existing = dao.cameraSet(id)
                if (existing?.registered == true) return
                dao.salvarCameraSet(CameraValidatedSet(id, boss.name, reps, closed,
                    if (existing?.closed == true) existing.completedAt else now))
            }
            state.completedSets.forEachIndexed { index, reps -> checkpointSet(index, reps, true) }
            checkpointSet(state.completedSets.size, state.attemptReps, false)
            dao.salvarCameraBoss(old.copy(
                stage = old.stage + won, wins = old.wins + won,
                damage = state.attemptReps.coerceIn(0, CameraTrials.enemy(boss, state.rank).hp - 1),
                pendingReps = (old.pendingReps.toLong() + state.totalReps - previousReps).coerceAtMost(100_000).toInt(),
                trialRank = state.rank.ordinal, bestTrialRank = maxOf(old.bestTrialRank, state.bestRank),
                deaths = old.deaths + died + if (!same && old.damage > 0) 1 else 0,
                lastSessionId = sessionId, lastSessionReps = state.totalReps,
                lastSessionWins = state.victories, lastSessionDeaths = state.deaths, lastSessionRevision = state.revision,
            ))
        }
    }

    suspend fun registrarSeriesCameraPendentes(exercise: Exercicio, boss: CameraBoss): List<Pair<Int, RegistroSerie>> = withContext(NonCancellable) {
        db.withTransaction {
            var sets = dao.cameraSetsPendentes(boss.name)
            if (sets.isEmpty()) {
                val count = dao.cameraBoss(boss.name)?.pendingReps ?: 0
                if (count > 0) {
                    dao.salvarCameraSet(CameraValidatedSet("legacy:${boss.name}", boss.name, count.coerceAtMost(500), true, 0))
                    sets = dao.cameraSetsPendentes(boss.name)
                }
            }
            buildList {
                for (pendingSet in sets) {
                    val set = if (pendingSet.closed) pendingSet else pendingSet.copy(closed = true).also { dao.salvarCameraSet(it) }
                    val result = registrarSerie(exercise, null, set.reps, null, cameraBoss = boss, cameraSetId = set.id)
                        ?: break
                    add(set.reps to result)
                    if (result is RegistroSerie.Bloqueado) break
                }
            }
        }
    }

    /** The session id makes repeated analyzer callbacks idempotent. Every accepted rep is durable. */
    suspend fun registrarRepeticoesCamera(boss: CameraBoss, sessionId: String, sessionReps: Int) =
        withContext(NonCancellable) {
            db.withTransaction {
                val old = dao.cameraBoss(boss.name) ?: CameraBossProgress(boss.name)
                val previous = if (old.lastSessionId == sessionId) old.lastSessionReps else 0
                val accepted = sessionReps.coerceAtLeast(previous) - previous
                if (accepted <= 0) return@withTransaction
                val advanced = BossCampaign.advance(boss, old.stage, old.damage, old.wins, accepted)
                dao.salvarCameraBoss(old.copy(
                    stage = advanced.stage, damage = advanced.damage, wins = advanced.wins,
                    pendingReps = (old.pendingReps.toLong() + accepted).coerceAtMost(100_000).toInt(),
                    lastSessionId = sessionId, lastSessionReps = sessionReps,
                ))
            }
        }

    private val rotinaAdapter = Moshi.Builder().build().adapter(RotinaItens::class.java)

    // -----------------------------------------------------------------------
    // Competitive history
    // -----------------------------------------------------------------------

    fun snapshotsRecentes(limite: Int = 30): Flow<List<PowerSnapshot>> = dao.snapshotsRecentes(limite)

    suspend fun snapshotAteDia(dia: Long): PowerSnapshot? = dao.snapshotAteDia(dia)

    fun picoTemporada(numero: Int): Flow<TemporadaResumo?> = dao.temporadaResumoFlow(numero)

    // -----------------------------------------------------------------------
    // Nutrition
    // -----------------------------------------------------------------------

    fun refeicoesDoDia(dia: Long): Flow<List<Refeicao>> = dao.refeicoesDoDia(dia)

    fun totalNutricaoDoDia(dia: Long): Flow<TotalNutricao> = dao.totalNutricaoDoDia(dia)

    suspend fun adicionarRefeicao(r: Refeicao): Long = withContext(NonCancellable) {
        validarRefeicao(r)
        dao.inserirRefeicao(r)
    }

    suspend fun removerRefeicao(r: Refeicao) = withContext(NonCancellable) {
        dao.apagarRefeicao(r)
    }

    fun refeicoesPeriodo(inicio: Long, fim: Long) = dao.refeicoesPeriodo(inicio, fim)
    val alimentosSalvos = dao.alimentosSalvos()
    fun aguaDoDia(dia: Long) = dao.aguaDoDia(dia)
    suspend fun salvarAlimento(r: AlimentoSalvo) = withContext(NonCancellable) {
        com.liferpg.domain.NutritionFoodCodec.ler(r.alimentoJson)
        require(r.porcaoG.isFinite() && r.porcaoG in 1f..3000f && r.id.length in 1..300)
        dao.salvarAlimento(r)
    }
    suspend fun apagarAlimento(r: AlimentoSalvo) = withContext(NonCancellable) { dao.apagarAlimento(r) }
    suspend fun registrarAgua(dia: Long, ml: Int) = withContext(NonCancellable) {
        require(ml in 50..2000)
        db.withTransaction {
            dao.inserirAgua(AguaRegistro(epochDay = dia, ml = ml, timestamp = System.currentTimeMillis()))
            val p=dao.perfil()
            if(p==null || dia!=LocalDate.now().toEpochDay()) return@withTransaction null
            val reward=atualizarMissoesInterno(dia)
            if(reward.xp>0) dao.salvarPerfil(p.copy(xpTotal=p.xpTotal+reward.xp))
            resultadoMissoes(p,reward)
        }
    }
    suspend fun desfazerAgua(dia: Long) = withContext(NonCancellable) { dao.desfazerAgua(dia) }
    suspend fun editarRefeicao(r: Refeicao) = withContext(NonCancellable) { validarRefeicao(r); dao.atualizarRefeicao(r) }
    suspend fun adicionarPrato(rows: List<Refeicao>) = withContext(NonCancellable) {
        require(rows.size in 1..6)
        rows.forEach(::validarRefeicao)
        val ids = rows.mapNotNull { it.registroId }
        require(ids.distinct().size == ids.size)
        db.withTransaction {
            val existing = if (ids.isEmpty()) 0 else dao.contarRegistrosNutri(ids)
            // A process may die after commit but before its draft is cleared. Retry is idempotent.
            if (existing == rows.size) return@withTransaction
            require(existing == 0) { "Este prato já foi registrado. Confira o diário antes de repetir." }
            dao.inserirRefeicoes(rows)
        }
    }
    private fun validarRefeicao(r: Refeicao) {
        require(r.nome.isNotBlank() && r.nome.length <= 160 && r.porcaoG.isFinite() && r.porcaoG in 1f..3000f)
        require(listOf(r.proteinaG, r.carboG, r.gorduraG, r.calorias).all { it.isFinite() && it in 0f..100_000f })
        require(r.fibraG == null || r.fibraG.isFinite() && r.fibraG in 0f..3000f)
        require(r.categoria in com.liferpg.domain.MomentoRefeicao.entries.map { it.name })
        require(r.registroId == null || r.registroId.length in 1..200)
    }
    suspend fun salvarMetasNutri(p: Preferencias, idade: Int, altura: Int, sexo: String) = withContext(NonCancellable) {
        require(idade in 0..120 && altura in 0..260 && sexo in com.liferpg.domain.Sexo.entries.map { it.name })
        require(p.metaCalorias in 0..10_000 && p.metaProteinaG in 0..600 && p.metaCarboG in 0..1500 && p.metaGorduraG in 0..500)
        db.withTransaction { dao.atualizarDadosNutri(idade, altura, sexo); dao.salvarPreferencias(p) }
    }

    // -----------------------------------------------------------------------
    // Routines
    // -----------------------------------------------------------------------

    suspend fun salvarRotina(
        nome: String,
        itens: List<ItemRotinaDef>,
        diasSemana: Set<Int> = emptySet(),
        id: Long = 0L,
    ): Long =
        withContext(NonCancellable) {
            dao.salvarRotina(
                RotinaCustom(
                    id = id,
                    nome = nome.trim().ifBlank { "Minha rotina" },
                    itensJson = rotinaAdapter.toJson(
                        RotinaItens(itens, diasSemana.filter { it in 1..7 }.sorted())
                    ),
                    criadoEm = System.currentTimeMillis(),
                )
            )
        }

    suspend fun apagarRotina(id: Long) = withContext(NonCancellable) {
        dao.apagarRotina(id)
    }

    fun decodificarItensRotina(json: String): List<ItemRotinaDef> =
        runCatching { rotinaAdapter.fromJson(json)?.itens }.getOrNull() ?: emptyList()

    fun codificarItensRotina(itens: List<ItemRotinaDef>): String = rotinaAdapter.toJson(RotinaItens(itens))

    fun decodificarDiasRotina(json: String): Set<Int> =
        runCatching { rotinaAdapter.fromJson(json)?.diasSemana.orEmpty().filter { it in 1..7 }.toSet() }
            .getOrDefault(emptySet())

    // -----------------------------------------------------------------------
    // Sessions and sets
    // -----------------------------------------------------------------------

    fun sessoesRecentes(limite: Int = 50): Flow<List<TreinoSessao>> = dao.sessoesRecentes(limite)
    val arquivoSessoes = dao.arquivoSessoes()
    val arquivoSeries = dao.arquivoSeries()
    fun totalSessoes(): Flow<Int> = dao.totalSessoes()
    suspend fun resumoCompartilhavel(uuid: String) = dao.sessaoPorUuid(uuid)?.receiptJson?.isNotBlank() == true

    suspend fun salvarSessao(s: TreinoSessao): Long = withContext(NonCancellable) {
        dao.salvarSessao(s)
    }

    fun seriesDoDia(dia: Long): Flow<List<SerieRegistro>> = dao.seriesDoDia(dia)

    fun missoesDoDia(dia: Long): Flow<List<MissaoDiaria>> = dao.missoesDoDia(dia)
    fun missoesSemana(day: Long) = dao.systemWeek(MissaoEngine.inicioSemana(day))
    fun historicoMissoes(day: Long) = dao.systemHistory(day-89,day)
    fun podeAdaptarMissoes(day: Long) = combine(dao.missoesDoDia(day),dao.seriesDoDia(day),dao.cardioHistory(),dao.aguaDoDia(day)) { missions, sets, cardio, water ->
        missions.isNotEmpty() && missions.none { it.progresso>0 || it.concluida } && sets.none { it.valido } &&
            cardio.none { it.epochDay==day } && water.isEmpty()
    }

    fun ultimasSeries(limite: Int = 50): Flow<List<SerieRegistro>> = dao.ultimasSeries(limite)

    fun seriesDoExercicio(exercicioId: String, limite: Int = 20): Flow<List<SerieRegistro>> =
        dao.seriesDoExercicio(exercicioId, limite)

    fun seriesDesde(desde: Long): Flow<List<SerieRegistro>> = dao.seriesDesde(desde)

    // -----------------------------------------------------------------------
    // Profile and body weight
    // -----------------------------------------------------------------------

    suspend fun criarPerfil(perfil: Perfil, preferencias: Preferencias,
        dias: Set<Int> = com.liferpg.domain.StarterPlan.diasPadrao(preferencias.diasPorSemana),
        foco: com.liferpg.domain.Musculo? = null): Boolean = withContext(NonCancellable) {
        val rotinasIniciais = com.liferpg.domain.StarterPlan.criar(catalogo.exercicios(), dias,
            com.liferpg.domain.Objetivo.seguro(preferencias.objetivo),
            com.liferpg.domain.NivelExperiencia.seguro(preferencias.experiencia),
            com.liferpg.domain.LocalTreino.entries.firstOrNull { it.name == preferencias.localPreferido }
                ?: com.liferpg.domain.LocalTreino.ACADEMIA, foco)
        db.withTransaction {
            // Double taps, a restored draft, or a late auth callback must never reset an existing save.
            if (dao.perfil() != null) return@withTransaction false
            dao.salvarPerfil(perfil)
            dao.salvarPreferencias(preferencias)
            if (dao.todasRotinas().isEmpty()) {
                rotinasIniciais.forEach { (nome, dias, itens) ->
                    dao.salvarRotina(
                        RotinaCustom(
                            nome = nome,
                            itensJson = rotinaAdapter.toJson(RotinaItens(itens, dias.sorted())),
                            criadoEm = System.currentTimeMillis(),
                        )
                    )
                }
            }
            true
        }
    }

    suspend fun atualizarPeso(pesoKg: Float) = withContext(NonCancellable) {
        dao.atualizarPesoCorporal(ValidadorTreino.pesoCorporalSano(pesoKg))
    }

    fun pesoDesde(desde: Long): Flow<List<PesoCorporal>> = dao.pesoDesde(desde)

    suspend fun ultimoPesoCorporal(): PesoCorporal? = dao.ultimoPeso()

    suspend fun registrarPesoCorporal(pesoKg: Float) {
        val sano = ValidadorTreino.pesoCorporalSano(pesoKg)
        val hoje = LocalDate.now().toEpochDay()
        withContext(NonCancellable) {
            db.withTransaction {
                dao.salvarPeso(PesoCorporal(hoje, sano, System.currentTimeMillis()))
                dao.atualizarPesoCorporal(sano)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Backup
    // -----------------------------------------------------------------------

    suspend fun exportarBackupJson(): String = withContext(NonCancellable) {
        db.withTransaction {
        val perfil = dao.perfil()
        val prefs = dao.preferencias()
        val series = dao.todasSeries()
        val sessoes = dao.todasSessoes()
        val pesos = dao.todosPesos()
        val refeicoes = dao.todasRefeicoes()
        val agua = dao.todaAgua()
        val favoritos = dao.todosAlimentosSalvos()
        val rotinas = dao.todasRotinas()
        val cameraBosses = dao.todosCameraBosses()
        val cameraSets = dao.todosCameraSetsPendentes()

        val raiz = JSONObject()
        raiz.put("app", "REMNANT")
        raiz.put("schema", 15)
        raiz.put("cardio", JSONArray().apply { dao.allCardio().forEach { put(CardioCodec.encode(it)) } })
        raiz.put("evolution", JSONArray().apply { dao.evolutionEntries().forEach { row ->
            put(JSONObject().put("id", row.id).put("payload", row.payload))
        } })
        raiz.put("missions", JSONArray().apply { dao.allMissions().forEach { row -> put(JSONObject().apply {
            put("day", row.epochDay); put("type", row.tipo); put("description", row.descricao); put("muscle", row.alvoMusculo ?: JSONObject.NULL)
            put("target", row.alvoQtd); put("progress", row.progresso); put("done", row.concluida); put("xp", row.xpRecompensa)
        }) } })
        raiz.put("achievements", JSONArray().apply { dao.allAchievements().forEach { row -> put(JSONObject().put("id", row.id).put("time", row.desbloqueadaEm)) } })
        raiz.put("snapshots", JSONArray().apply { dao.allSnapshots().forEach { row -> put(JSONObject().put("day", row.epochDay).put("power", row.powerScore.toDouble()).put("rank", row.patente).put("lp", row.pontosLiga)) } })
        raiz.put("seasons", JSONArray().apply { dao.allSeasons().forEach { row -> put(JSONObject().put("number", row.numero).put("rank", row.patentePico).put("power", row.powerScorePico.toDouble()).put("lp", row.pontosLigaPico).put("time", row.encerradaEm)) } })
        raiz.put("exportadoEm", System.currentTimeMillis())

        if (perfil != null) {
            raiz.put(
                "perfil",
                JSONObject().apply {
                    put("nome", perfil.nome)
                    put("classe", perfil.classe)
                    put("pesoCorporalKg", perfil.pesoCorporalKg.toDouble())
                    put("sexo", perfil.sexo)
                    put("idade", perfil.idade)
                    put("alturaCm", perfil.alturaCm)
                    put("xpTotal", perfil.xpTotal)
                    put("streak", perfil.streak)
                    put("melhorStreak", perfil.melhorStreak)
                    put("ultimoDiaTreino", perfil.ultimoDiaTreino)
                    put("criadoEm", perfil.criadoEm)
                    put("lema", perfil.lema)
                    put("estiloMoldura", perfil.estiloMoldura)
                    put("biografia", perfil.biografia)
                    put("focoTreino", perfil.focoTreino)
                    put("seloPerfil", perfil.seloPerfil)
                }
            )
        }
        if (prefs != null) {
            raiz.put(
                "preferencias",
                JSONObject().apply {
                    put("objetivo", prefs.objetivo)
                    put("experiencia", prefs.experiencia)
                    put("localPreferido", prefs.localPreferido)
                    put("diasPorSemana", prefs.diasPorSemana)
                    put("descansoPadraoSeg", prefs.descansoPadraoSeg)
                    put("metaProteinaG", prefs.metaProteinaG)
                    put("lembretesAtivos", prefs.lembretesAtivos)
                    put("horarioTreino", prefs.horarioTreino)
                    put("metaCalorias", prefs.metaCalorias)
                    put("metaCarboG", prefs.metaCarboG); put("metaGorduraG", prefs.metaGorduraG)
                    put("metasNutricaoManuais", prefs.metasNutricaoManuais)
                    put("atividadeNutri", prefs.atividadeNutri); put("objetivoNutri", prefs.objetivoNutri)
                    put("condicoes", prefs.condicoes)
                    put("horarioTreino", prefs.horarioTreino)
                    put("tituloEquipado", prefs.tituloEquipado ?: JSONObject.NULL)
                }
            )
        }

        val arrSeries = JSONArray()
        for (s in series) {
            arrSeries.put(
                JSONObject().apply {
                    put("exercicioId", s.exercicioId)
                    put("musculo", s.musculo)
                    put("pesoKg", s.pesoKg?.toDouble() ?: JSONObject.NULL)
                    put("reps", s.reps ?: JSONObject.NULL)
                    put("segundos", s.segundos ?: JSONObject.NULL)
                    put("score", s.score.toDouble())
                    put("xpGanho", s.xpGanho)
                    put("epochDay", s.epochDay)
                    put("timestamp", s.timestamp)
                    put("valido", s.valido)
                    put("esforco", s.esforco); put("equipamento", s.equipamento); put("origem", s.origem)
                }
            )
        }
        raiz.put("series", arrSeries)

        val arrSessoes = JSONArray()
        for (t in sessoes) {
            arrSessoes.put(
                JSONObject().apply {
                    put("epochDay", t.epochDay)
                    put("nome", t.nome)
                    put("xpGanho", t.xpGanho)
                    put("series", t.series)
                    put("exercicios", t.exercicios)
                    put("recordes", t.recordes)
                    put("duracaoSeg", t.duracaoSeg)
                    put("itensJson", t.itensJson)
                    put("sessionUuid", t.sessionUuid); put("receiptJson", t.receiptJson)
                    put("encerradaEm", t.encerradaEm)
                }
            )
        }
        raiz.put("sessoes", arrSessoes)

        val arrPesos = JSONArray()
        for (p in pesos) {
            arrPesos.put(
                JSONObject().apply {
                    put("epochDay", p.epochDay)
                    put("pesoKg", p.pesoKg.toDouble())
                    put("timestamp", p.timestamp)
                }
            )
        }
        raiz.put("peso", arrPesos)

        raiz.put("refeicoes", JSONArray().apply {
            refeicoes.forEach { r -> put(JSONObject().apply {
                put("epochDay", r.epochDay); put("nome", r.nome); put("proteinaG", r.proteinaG.toDouble())
                put("calorias", r.calorias.toDouble()); put("carboG", r.carboG.toDouble()); put("gorduraG", r.gorduraG.toDouble())
                put("porcaoG", r.porcaoG.toDouble()); put("barcode", r.barcode ?: JSONObject.NULL); put("timestamp", r.timestamp)
                put("categoria", r.categoria); put("fonte", r.fonte); put("estimado", r.estimado)
                put("fibraG", r.fibraG?.toDouble() ?: JSONObject.NULL); put("macrosCompletos", r.macrosCompletos)
                put("registroId", r.registroId ?: JSONObject.NULL)
            }) }
        })
        raiz.put("agua", JSONArray().apply { agua.forEach { r -> put(JSONObject().put("epochDay", r.epochDay).put("ml", r.ml).put("timestamp", r.timestamp)) } })
        raiz.put("alimentosSalvos", JSONArray().apply { favoritos.forEach { r -> put(JSONObject().put("id", r.id).put("nome", r.nome).put("alimentoJson", r.alimentoJson).put("porcaoG", r.porcaoG.toDouble())) } })
        raiz.put("rotinas", JSONArray().apply {
            rotinas.forEach { r -> put(JSONObject().apply {
                put("nome", r.nome); put("itensJson", r.itensJson); put("criadoEm", r.criadoEm)
            }) }
        })
        raiz.put("cameraBosses", JSONArray().apply {
            cameraBosses.forEach { b -> put(JSONObject().apply {
                put("bossId", b.bossId); put("stage", b.stage); put("damage", b.damage)
                put("wins", b.wins); put("pendingReps", b.pendingReps)
                put("trialRank", b.trialRank); put("bestTrialRank", b.bestTrialRank); put("deaths", b.deaths)
            }) }
        })
        raiz.put("cameraSets", JSONArray().apply {
            cameraSets.forEach { s -> put(JSONObject().apply {
                put("id", s.id); put("bossId", s.bossId); put("reps", s.reps)
                put("completedAt", s.completedAt)
            }) }
        })

        raiz.toString(2)
        }
    }

    /** Validates the whole portable document before replacing any local row. */
    suspend fun restaurarBackupJson(source: String, authorized: () -> Boolean = { true }): BackupRestoreResult = withContext(NonCancellable) {
        require(source.toByteArray(Charsets.UTF_8).size <= 10 * 1024 * 1024) { "O backup ultrapassa 10 MB." }
        val root = runCatching { JSONObject(source) }.getOrElse { throw IllegalArgumentException("O arquivo não contém JSON válido.") }
        require(root.optString("app") == "REMNANT") { "Este arquivo não é um backup do Remnant." }
        val schema = root.optInt("schema", -1)
        require(schema in 7..15) { "Versão de backup não suportada: $schema." }

        fun text(o: JSONObject, key: String, max: Int): String = o.getString(key).trim().also {
            require(it.isNotEmpty() && it.length <= max) { "Campo inválido: $key." }
        }
        fun nullableText(o: JSONObject, key: String, max: Int): String? =
            if (!o.has(key) || o.isNull(key)) null else o.getString(key).trim().takeIf { it.isNotEmpty() }?.also {
                require(it.length <= max) { "Campo inválido: $key." }
            }
        fun array(name: String, max: Int): List<JSONObject> {
            val a = root.optJSONArray(name) ?: JSONArray()
            require(a.length() <= max) { "O backup contém registros demais em $name." }
            return (0 until a.length()).map { a.getJSONObject(it) }
        }
        fun finiteFloat(o: JSONObject, key: String, min: Float = -Float.MAX_VALUE, max: Float = Float.MAX_VALUE): Float {
            val value = o.getDouble(key).toFloat()
            require(value.isFinite() && value in min..max) { "Valor inválido: $key." }
            return value
        }

        val profile = root.optJSONObject("perfil")?.let { o ->
            Perfil(
                nome = text(o, "nome", 80), classe = text(o, "classe", 40),
                pesoCorporalKg = finiteFloat(o, "pesoCorporalKg", 20f, 500f), sexo = o.optString("sexo", "OUTRO").take(20),
                idade = o.optInt("idade", 0).coerceIn(0, 130), alturaCm = o.optInt("alturaCm", 0).coerceIn(0, 260),
                xpTotal = o.optLong("xpTotal", 0).coerceAtLeast(0), streak = o.optInt("streak", 0).coerceAtLeast(0),
                melhorStreak = o.optInt("melhorStreak", 0).coerceAtLeast(0), ultimoDiaTreino = o.optLong("ultimoDiaTreino", 0),
                criadoEm = o.optLong("criadoEm", System.currentTimeMillis()),
                lema = o.optString("lema", "").take(90),
                estiloMoldura = o.optString("estiloMoldura", "CINZAS").takeIf { it in com.liferpg.domain.IdentidadePerfil.molduras.keys } ?: "CINZAS",
                biografia = o.optString("biografia", "").take(240),
                focoTreino = o.optString("focoTreino", "EQUILIBRIO").takeIf { it in com.liferpg.domain.IdentidadePerfil.focos.keys } ?: "EQUILIBRIO",
                seloPerfil = o.optString("seloPerfil", "ESPADA").takeIf { it in com.liferpg.domain.IdentidadePerfil.selos.keys } ?: "ESPADA",
            )
        }
        val preferences = root.optJSONObject("preferencias")?.let { o ->
            Preferencias(
                objetivo = text(o, "objetivo", 40), experiencia = text(o, "experiencia", 40), localPreferido = text(o, "localPreferido", 40),
                diasPorSemana = o.optInt("diasPorSemana", 3).coerceIn(1, 7), descansoPadraoSeg = o.optInt("descansoPadraoSeg", 90).coerceIn(15, 600),
                metaProteinaG = o.optInt("metaProteinaG", 0).coerceIn(0, 1000), rotinaAtiva = nullableText(o, "rotinaAtiva", 100),
                lembretesAtivos = o.optBoolean("lembretesAtivos", false), condicoes = o.optString("condicoes", "").take(500),
                horarioTreino = o.optString("horarioTreino", "QUALQUER").take(40), metaCalorias = o.optInt("metaCalorias", 0).coerceIn(0, 20_000),
                tituloEquipado = nullableText(o, "tituloEquipado", 100),
                metaCarboG = o.optInt("metaCarboG", 0).coerceIn(0, 1500), metaGorduraG = o.optInt("metaGorduraG", 0).coerceIn(0, 500),
                metasNutricaoManuais = o.optBoolean("metasNutricaoManuais", false),
                atividadeNutri = o.optString("atividadeNutri", "LEVE").takeIf { it in listOf("BAIXA", "LEVE", "MODERADA", "ALTA") } ?: "LEVE",
                objetivoNutri = o.optString("objetivoNutri", "MANTER").takeIf { it in listOf("MANTER", "GANHAR", "REDUZIR") } ?: "MANTER",
            )
        }
        val sets = array("series", 100_000).map { o ->
            SerieRegistro(
                exercicioId = text(o, "exercicioId", 160), musculo = text(o, "musculo", 40),
                pesoKg = if (o.isNull("pesoKg")) null else finiteFloat(o, "pesoKg", 0f, 2000f),
                reps = if (o.isNull("reps")) null else o.getInt("reps").coerceIn(0, 100_000),
                segundos = if (o.isNull("segundos")) null else o.getInt("segundos").coerceIn(0, 86_400),
                score = finiteFloat(o, "score", 0f), xpGanho = o.optInt("xpGanho", 0).coerceIn(0, 1_000_000),
                epochDay = o.getLong("epochDay"), timestamp = o.getLong("timestamp"), valido = o.optBoolean("valido", true),
                esforco = o.optString("esforco", "").takeIf { it.isEmpty() || it in com.liferpg.domain.SetEffort.entries.map { value -> value.name } } ?: "",
                equipamento = o.optString("equipamento", "").take(60),
                origem = o.optString("origem", "MANUAL").takeIf { it in listOf("MANUAL", "CAMERA") } ?: "MANUAL",
            )
        }
        val sessions = array("sessoes", 20_000).map { o -> TreinoSessao(
            epochDay = o.getLong("epochDay"), nome = text(o, "nome", 120), xpGanho = o.optInt("xpGanho", 0).coerceAtLeast(0),
            series = o.optInt("series", 0).coerceAtLeast(0), exercicios = o.optInt("exercicios", 0).coerceAtLeast(0),
            recordes = o.optInt("recordes", 0).coerceAtLeast(0), duracaoSeg = o.optInt("duracaoSeg", 0).coerceIn(0, 604_800),
            encerradaEm = o.getLong("encerradaEm"),
            itensJson = o.optString("itensJson", "").take(200_000),
            sessionUuid = o.optString("sessionUuid").takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess },
            receiptJson = o.optString("receiptJson", "").also { if(it.isNotBlank()) com.liferpg.data.sync.CircleCodec.validateReceipt(it) },
        ) }
        require(sessions.mapNotNull { it.sessionUuid }.let { it.distinct().size==it.size }) { "Identificadores de sessão duplicados." }
        val weights = array("peso", 20_000).map { o -> PesoCorporal(o.getLong("epochDay"), finiteFloat(o, "pesoKg", 20f, 500f), o.getLong("timestamp")) }
        val meals = array("refeicoes", 100_000).map { o -> Refeicao(
            epochDay = o.getLong("epochDay"), nome = text(o, "nome", 160), proteinaG = finiteFloat(o, "proteinaG", 0f, 10_000f),
            calorias = finiteFloat(o, "calorias", 0f, 100_000f), carboG = finiteFloat(o, "carboG", 0f, 10_000f),
            gorduraG = finiteFloat(o, "gorduraG", 0f, 10_000f), porcaoG = finiteFloat(o, "porcaoG", 0f, 100_000f),
            barcode = nullableText(o, "barcode", 100), timestamp = o.getLong("timestamp"),
            categoria = com.liferpg.domain.MomentoRefeicao.seguro(o.optString("categoria", "OUTROS")).name,
            fonte = o.optString("fonte", "Registro anterior").take(120), estimado = o.optBoolean("estimado", false),
            fibraG = if (!o.has("fibraG") || o.isNull("fibraG")) null else finiteFloat(o, "fibraG", 0f, 10_000f),
            macrosCompletos = o.optBoolean("macrosCompletos", false),
            registroId = nullableText(o, "registroId", 200),
        ) }
        val mealKeys = meals.mapNotNull { it.registroId }
        require(mealKeys.distinct().size == mealKeys.size) { "Refeições duplicadas no backup." }
        val water = array("agua", 100_000).map { o ->
            val ml = o.getInt("ml"); require(ml in 50..2000) { "Registro de água inválido." }
            AguaRegistro(epochDay = o.getLong("epochDay"), ml = ml, timestamp = o.getLong("timestamp"))
        }
        val savedFoods = array("alimentosSalvos", 2000).map { o ->
            val raw = text(o, "alimentoJson", 6000)
            com.liferpg.domain.NutritionFoodCodec.ler(raw)
            AlimentoSalvo(text(o, "id", 300), text(o, "nome", 160), raw, finiteFloat(o, "porcaoG", 1f, 3000f))
        }
        require(savedFoods.map { it.id }.distinct().size == savedFoods.size) { "Alimentos salvos duplicados." }
        val routines = array("rotinas", 2_000).map { o -> RotinaCustom(
            nome = text(o, "nome", 120), itensJson = text(o, "itensJson", 200_000), criadoEm = o.getLong("criadoEm"),
        ) }
        val bosses = array("cameraBosses", 2).map { o ->
            val boss = CameraBoss.entries.firstOrNull { it.name == o.optString("bossId") }
                ?: throw IllegalArgumentException("Chefe inválido no backup.")
            val stage = o.optInt("stage", 0).coerceIn(0, 10_000)
            CameraBossProgress(
                bossId = boss.name, stage = stage,
                damage = 0, // An active attempt cannot be resumed from a portable backup.
                wins = o.optInt("wins", 0).coerceIn(0, 10_000),
                pendingReps = o.optInt("pendingReps", 0).coerceIn(0, 100_000),
                trialRank = o.optInt("trialRank", 1).coerceIn(1, 14),
                bestTrialRank = o.optInt("bestTrialRank", 0).coerceIn(0, 14),
                deaths = o.optInt("deaths", 0).coerceAtLeast(0),
            )
        }
        val cameraSets = array("cameraSets", 10_000).map { o ->
            val boss = CameraBoss.entries.firstOrNull { it.name == o.optString("bossId") }
                ?: throw IllegalArgumentException("Série da câmera com chefe inválido.")
            val reps = o.getInt("reps")
            require(reps in 1..500) { "Repetições inválidas na série da câmera." }
            CameraValidatedSet(text(o, "id", 200), boss.name, reps, true, o.optLong("completedAt", 0))
        }.toMutableList()
        require(cameraSets.map { it.id }.distinct().size == cameraSets.size) { "Séries da câmera duplicadas." }
        require(cameraSets.all { set -> bosses.any { it.bossId == set.bossId } }) { "Série da câmera sem progresso correspondente." }
        bosses.forEach { b ->
            val recorded = cameraSets.filter { it.bossId == b.bossId }.sumOf { it.reps }
            require(recorded <= b.pendingReps) { "Total de séries da câmera inválido." }
            if (recorded < b.pendingReps) {
                // Legacy backups know only the aggregate pending count.
                var remaining = b.pendingReps - recorded
                var index = 0
                while (remaining > 0) {
                    val reps = minOf(remaining, 500)
                    var id: String
                    do { id = "import:${b.bossId}:${index++}" } while (cameraSets.any { it.id == id })
                    cameraSets += CameraValidatedSet(id, b.bossId, reps, true, 0)
                    remaining -= reps
                }
            }
        }

        val evolution = array("evolution", 4000).map { o ->
            EvolutionEntry(text(o, "id", 180), text(o, "payload", 100_000)).also { com.liferpg.domain.EvolutionCodec.validate(it) }
        }
        require(evolution.map { it.id }.distinct().size == evolution.size) { "Registros de evolução duplicados." }
        val cardio = array("cardio", 20_000).map(CardioCodec::decode)
        require(cardio.map { it.id }.distinct().size == cardio.size) { "Registros de cardio duplicados." }
        val missions = array("missions", 100_000).map { o -> MissaoDiaria(epochDay=o.getLong("day"), tipo=text(o,"type",60), descricao=text(o,"description",500),
            alvoMusculo=nullableText(o,"muscle",40), alvoQtd=o.getInt("target").coerceIn(1,100_000), progresso=o.getInt("progress").coerceIn(0,100_000),
            concluida=o.getBoolean("done"), xpRecompensa=o.getInt("xp").coerceIn(0,100_000)) }
        val achievements = array("achievements", 10_000).map { o -> ConquistaDesbloqueada(text(o,"id",180),o.getLong("time")) }
        val snapshots = array("snapshots", 100_000).map { o -> PowerSnapshot(o.getLong("day"),finiteFloat(o,"power",0f,100_000f),o.getInt("rank").coerceIn(0,15),o.getInt("lp").coerceAtLeast(0)) }
        val seasons = array("seasons", 10_000).map { o -> TemporadaResumo(o.getInt("number"),o.getInt("rank").coerceIn(0,15),finiteFloat(o,"power",0f,100_000f),o.getInt("lp").coerceAtLeast(0),o.getLong("time")) }
        db.withTransaction {
            check(authorized()) { "A conta mudou durante a restauração." }
            dao.resetTotal()
            profile?.let { dao.salvarPerfil(it) }
            preferences?.let { dao.salvarPreferencias(it) }
            sets.forEach { dao.inserirSerie(it) }
            sessions.forEach { dao.salvarSessao(it) }
            weights.forEach { dao.salvarPeso(it) }
            meals.forEach { dao.inserirRefeicao(it) }
            water.forEach { dao.inserirAgua(it) }
            savedFoods.forEach { dao.salvarAlimento(it) }
            routines.forEach { dao.salvarRotina(it) }
            bosses.forEach { dao.salvarCameraBoss(it) }
            cameraSets.forEach { dao.salvarCameraSet(it) }
            evolution.forEach { dao.saveEvolution(it) }
            cardio.forEach { dao.saveCardio(it) }
            if (missions.isNotEmpty()) dao.inserirMissoes(missions)
            achievements.forEach { dao.desbloquearConquista(it) }
            snapshots.forEach { dao.salvarSnapshot(it) }
            seasons.forEach { dao.salvarTemporadaResumo(it) }
            if (profile != null) garantirMissoesInterno(LocalDate.now().toEpochDay())
            check(authorized()) { "A conta mudou durante a restauração." }
        }
        routes?.clear()
        BackupRestoreResult(sets.size, sessions.size, routines.size, meals.size)
    }

    suspend fun atualizarIdentidade(nome: String, lema: String, classe: String, estilo: String, biografia: String = "", foco: String = "EQUILIBRIO", selo: String = "ESPADA") {
        require(nome.isNotBlank() && nome.length <= 40)
        require(lema.length <= 90)
        require(classe in com.liferpg.domain.ClassePersonagem.entries.map { it.name })
        require(estilo in com.liferpg.domain.IdentidadePerfil.molduras.keys)
        require(biografia.length <= 240 && foco in com.liferpg.domain.IdentidadePerfil.focos.keys && selo in com.liferpg.domain.IdentidadePerfil.selos.keys)
        withContext(NonCancellable) { dao.atualizarIdentidade(nome.trim(), lema.trim(), classe, estilo, biografia.trim(), foco, selo) }
    }

    suspend fun resetTotal() = withContext(NonCancellable) {
        db.withTransaction {
            dao.resetTotal()
            garantirMissoesInterno(LocalDate.now().toEpochDay())
        }
        routes?.clear()
    }

    // -----------------------------------------------------------------------
    // Daily missions
    // -----------------------------------------------------------------------

    suspend fun garantirMissoes(epochDay: Long): ResultadoSerie? = withContext(NonCancellable) {
        db.withTransaction {
            val p=dao.perfil() ?: return@withTransaction null
            garantirMissoesInterno(epochDay)
            val reward=atualizarMissoesInterno(epochDay)
            if(reward.xp>0) dao.salvarPerfil(p.copy(xpTotal=p.xpTotal+reward.xp))
            resultadoMissoes(p,reward)
        }
    }
    suspend fun adaptarMissoes(day: Long, mode: com.liferpg.domain.SystemMode) = withContext(NonCancellable) {
        db.withTransaction {
            require(day==LocalDate.now().toEpochDay()) { "As ordens desse dia já encerraram." }
            check(dao.perfil()!=null) { "Entre na sua conta para escolher as ordens." }
            val orders=dao.systemDay(day)
            require(orders.none { it.progresso>0 || it.concluida } && dao.systemSets(day,day).isEmpty() &&
                dao.systemCardio(day,day).isEmpty() && dao.systemWater(day)==0) {
                "Suas ordens já começaram. Você pode escolher outra modalidade no próximo dia."
            }
            dao.replaceUnstartedOrders(day)
            dao.inserirMissoes(MissaoEngine.gerarMissoes(day,mode))
        }
    }

    // -----------------------------------------------------------------------
    // Competitive snapshot
    // -----------------------------------------------------------------------

    suspend fun registrarCompetitivo(
        epochDay: Long,
        temporada: Int,
        powerScore: Float,
        patenteOrdinal: Int,
        lp: Int,
    ) = withContext(NonCancellable) {
        db.withTransaction {
            dao.salvarSnapshot(PowerSnapshot(epochDay, powerScore, patenteOrdinal, lp))

            // Close the previous season the first time we see it after the rollover.
            val anterior = dao.temporadaResumo(temporada - 1)
            if (anterior != null && anterior.encerradaEm == 0L) {
                dao.salvarTemporadaResumo(anterior.copy(encerradaEm = System.currentTimeMillis()))
            }

            val atual = dao.temporadaResumo(temporada)
            if (atual == null || powerScore > atual.powerScorePico) {
                dao.salvarTemporadaResumo(
                    TemporadaResumo(
                        numero = temporada,
                        patentePico = max(atual?.patentePico ?: 0, patenteOrdinal),
                        powerScorePico = max(atual?.powerScorePico ?: 0f, powerScore),
                        pontosLigaPico = lp,
                        encerradaEm = 0L,
                    )
                )
            }
        }
    }

    // -----------------------------------------------------------------------
    // Achievements
    // -----------------------------------------------------------------------

    /** Returns only the ids that were actually new, so the UI can pop one toast each. */
    suspend fun desbloquearConquistas(ids: List<String>): List<String> = withContext(NonCancellable) {
        if (ids.isEmpty()) return@withContext emptyList()

        val novas = mutableListOf<String>()
        db.withTransaction {
            val ja = dao.idsConquistas().toSet()
            val agora = System.currentTimeMillis()
            for (id in ids) {
                if (id in ja) continue
                if (dao.desbloquearConquista(ConquistaDesbloqueada(id, agora)) != -1L) {
                    novas.add(id)
                }
            }
        }
        novas
    }

    // -----------------------------------------------------------------------
    // Preferences
    // -----------------------------------------------------------------------

    suspend fun salvarPreferencias(p: Preferencias) = withContext(NonCancellable) {
        dao.salvarPreferencias(p)
    }

    suspend fun preferenciasAtuais(): Preferencias? = dao.preferencias()

    // -----------------------------------------------------------------------
    // The core write: one set
    // -----------------------------------------------------------------------

    /**
     * Validates, scores and persists a single set, then advances missions, xp, level and
     * streak in the same transaction. Returns null when there is no profile yet.
     */
    suspend fun registrarSerie(
        exercicio: Exercicio,
        pesoKg: Float?,
        reps: Int?,
        segundos: Int?,
        cameraBoss: CameraBoss? = null,
        cameraSetId: String? = null,
        esforco: String = "",
        equipamento: String = "",
    ): RegistroSerie? = withContext(NonCancellable) {
        db.withTransaction<RegistroSerie?> {
            require(esforco.isEmpty() || esforco in com.liferpg.domain.SetEffort.entries.map { it.name })
            require(equipamento.length <= 60)
            val perfil = dao.perfil() ?: return@withTransaction null
            val cameraSet = cameraSetId?.let { dao.cameraSet(it) }
            if (cameraSetId != null && (cameraSet == null || cameraSet.registered || !cameraSet.closed ||
                    cameraSet.bossId != cameraBoss?.name || cameraSet.reps != reps || cameraBoss?.exerciseId != exercicio.id)) {
                return@withTransaction RegistroSerie.Bloqueado("Esta série da câmera já foi registrada ou está incompleta.")
            }
            val classe = runCatching { ClassePersonagem.valueOf(perfil.classe) }.getOrNull()
                ?: ClassePersonagem.AVENTUREIRO

            val agora = System.currentTimeMillis()
            val hoje = LocalDate.now().toEpochDay()
            val pesoCorporal = ValidadorTreino.pesoCorporalSano(perfil.pesoCorporalKg)
            val score = RankEngine.scoreSerie(exercicio.modo, pesoCorporal, pesoKg, reps, segundos)

            if (!score.isFinite() || score <= 0f || score > 100_000f) {
                return@withTransaction RegistroSerie.Bloqueado("Valor inválido para a série.")
            }

            val serieAnterior = dao.melhorSerieDe(exercicio.id)
            val melhorAnterior = serieAnterior?.score ?: 0f
            val msDesdeUltima = dao.ultimaSerieTimestamp()?.let { agora - it } ?: Long.MAX_VALUE
            val seriesHojeExercicio = dao.seriesValidasHojeExercicio(exercicio.id, hoje)

            val veredito = ValidadorTreino.validar(
                modo = exercicio.modo,
                pesoKg = pesoKg,
                reps = reps,
                segundos = segundos,
                score = score,
                benchmarks = exercicio.benchmarks,
                melhorAnterior = melhorAnterior,
                msDesdeUltima = if (cameraSet != null) Long.MAX_VALUE else msDesdeUltima,
                seriesHojeExercicio = seriesHojeExercicio,
            )
            if (veredito is Veredito.Rejeitado) {
                return@withTransaction RegistroSerie.Bloqueado(veredito.motivo)
            }

            val suspeito = veredito is Veredito.Suspeito
            val valido = !suspeito
            val novoRecorde = valido && (score > melhorAnterior || melhorAnterior <= 0f)
            val patenteAnterior = RankEngine.patenteDoScore(melhorAnterior, exercicio.benchmarks)
            val patenteNova = if (valido) {
                RankEngine.patenteDoScore(max(score, melhorAnterior), exercicio.benchmarks)
            } else {
                patenteAnterior
            }
            val subiuPatente = valido && patenteNova > patenteAnterior
            val xpSerie = if (valido) {
                com.liferpg.domain.Experience.calcular(exercicio, classe, score, novoRecorde, subiuPatente).total
            } else {
                0
            }

            dao.inserirSerie(
                SerieRegistro(
                    exercicioId = exercicio.id,
                    musculo = exercicio.musculo.name,
                    pesoKg = pesoKg,
                    reps = reps,
                    segundos = segundos,
                    score = score,
                    xpGanho = xpSerie,
                    epochDay = hoje,
                    timestamp = agora,
                    valido = valido,
                    esforco = esforco,
                    equipamento = equipamento.trim(),
                    origem = if (cameraSet != null) "CAMERA" else "MANUAL",
                )
            )
            if (cameraBoss != null && reps != null) {
                dao.cameraBoss(cameraBoss.name)?.let { old ->
                    dao.salvarCameraBoss(old.copy(pendingReps = (old.pendingReps - reps).coerceAtLeast(0)))
                }
            }
            if (cameraSet != null) dao.salvarCameraSet(cameraSet.copy(registered = true))

            var xpMissoes = 0
            val concluidasAgora = mutableListOf<MissaoDiaria>()
            if (valido) {
                garantirMissoesInterno(hoje)
                val reward=atualizarMissoesInterno(hoje,novoRecorde)
                xpMissoes=reward.xp
                concluidasAgora.addAll(reward.completed)
            }

            val nivelAnterior = RankEngine.nivelDoXp(perfil.xpTotal)
            val xpTotalNovo = perfil.xpTotal + xpSerie + xpMissoes
            val nivelNovo = RankEngine.nivelDoXp(xpTotalNovo)
            val streakNovo = if (valido) {
                RankEngine.novoStreak(perfil.streak, perfil.ultimoDiaTreino, hoje)
            } else {
                perfil.streak
            }
            dao.salvarPerfil(
                perfil.copy(
                    xpTotal = xpTotalNovo,
                    streak = streakNovo,
                    melhorStreak = max(perfil.melhorStreak, streakNovo),
                    ultimoDiaTreino = if (valido) hoje else perfil.ultimoDiaTreino,
                )
            )

            RegistroSerie.Ok(
                ResultadoSerie(
                    xpGanho = xpSerie + xpMissoes,
                    experiencia = if (valido) com.liferpg.domain.Experience.calcular(
                        exercicio, classe, score, novoRecorde, subiuPatente).copy(missoes = xpMissoes)
                        else com.liferpg.domain.ExperienceReward(),
                    novoRecorde = novoRecorde,
                    patenteAnterior = patenteAnterior,
                    patenteNova = patenteNova,
                    nivelAnterior = nivelAnterior,
                    nivelNovo = nivelNovo,
                    score = score,
                    missoesConcluidas = concluidasAgora,
                    conquistasNovas = emptyList(),
                    streakAtual = streakNovo,
                    primeiroTreinoDoDia = valido && perfil.ultimoDiaTreino != hoje,
                    recorde = if (novoRecorde) RecordePessoal(
                        exercicio.id, exercicio.nome, exercicio.modo,
                        serieAnterior?.let { MarcaPessoal(it.pesoKg, it.reps, it.segundos, it.score) },
                        MarcaPessoal(pesoKg, reps, segundos, score),
                    ) else null,
                ),
                suspeito,
            )
        }
    }

    /** Same as [garantirMissoes] but callable from inside an open transaction. */
    private suspend fun garantirMissoesInterno(epochDay: Long) {
        if(dao.perfil()==null) return
        if (dao.contarMissoesDoDia(epochDay) == 0) {
            val mode=if(dao.preferencias()?.localPreferido=="CASA") com.liferpg.domain.SystemMode.HOME else com.liferpg.domain.SystemMode.GYM
            dao.inserirMissoes(MissaoEngine.gerarMissoes(epochDay,mode))
        }
        if(dao.systemWeekCount(MissaoEngine.inicioSemana(epochDay))==0)
            dao.inserirMissoes(listOf(MissaoEngine.gerarSemana(epochDay,dao.preferencias()?.diasPorSemana ?: 3)))
    }
    private data class MissionReward(val xp: Int=0, val completed: List<MissaoDiaria> = emptyList())
    private suspend fun atualizarMissoesInterno(day: Long, newPr: Boolean=false): MissionReward {
        val week=MissaoEngine.inicioSemana(day)
        val pending=dao.systemOpen(day,week)
        if(pending.isEmpty()) return MissionReward()
        val facts=com.liferpg.domain.MissionFacts(day,dao.systemSets(week,day),dao.systemCardio(week,day),dao.systemWater(day))
        val won=mutableListOf<MissaoDiaria>()
        pending.forEach { old ->
            val next=MissaoEngine.avancar(old,facts,newPr)
            if(next.mission!=old) dao.atualizarMissao(next.mission)
            if(next.awarded) won+=next.mission
        }
        return MissionReward(won.sumOf { it.xpRecompensa },won)
    }
    private fun resultadoMissoes(p: Perfil, reward: MissionReward): ResultadoSerie? {
        if(reward.xp<=0) return null
        return ResultadoSerie(reward.xp,false,Patente.SEM_PATENTE,Patente.SEM_PATENTE,
            RankEngine.nivelDoXp(p.xpTotal),RankEngine.nivelDoXp(p.xpTotal+reward.xp),0f,reward.completed,
            emptyList(),p.streak,false,tipoRegistro="MISSAO")
    }
}

/** Outcome of [AppRepository.registrarSerie]: either persisted, or refused with a reason. */
sealed interface RegistroSerie {
    data class Ok(val resultado: ResultadoSerie, val suspeito: Boolean) : RegistroSerie

    data class Bloqueado(val motivo: String) : RegistroSerie
}

/** Everything the set screen needs to animate the reward, in one shot. */
data class ResultadoSerie(
    val xpGanho: Int,
    val novoRecorde: Boolean,
    val patenteAnterior: Patente,
    val patenteNova: Patente,
    val nivelAnterior: Int,
    val nivelNovo: Int,
    val score: Float,
    val missoesConcluidas: List<MissaoDiaria>,
    val conquistasNovas: List<String>,
    val streakAtual: Int = 0,
    val primeiroTreinoDoDia: Boolean = false,
    val recorde: RecordePessoal? = null,
    val experiencia: com.liferpg.domain.ExperienceReward? = null,
    val tipoRegistro: String = "SERIE",
)
