package com.liferpg.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

/** Single-row table: the profile always lives at id = 1. */
@Entity(tableName = "perfil")
data class Perfil(
    @PrimaryKey val id: Int = 1,
    val nome: String,
    val classe: String,
    val pesoCorporalKg: Float,
    @ColumnInfo(defaultValue = "'OUTRO'") val sexo: String = "OUTRO",
    @ColumnInfo(defaultValue = "0") val idade: Int = 0,
    @ColumnInfo(defaultValue = "0") val alturaCm: Int = 0,
    val xpTotal: Long = 0L,
    val streak: Int = 0,
    val melhorStreak: Int = 0,
    val ultimoDiaTreino: Long = 0L,
    val criadoEm: Long,
    @ColumnInfo(defaultValue = "''") val lema: String = "",
    @ColumnInfo(defaultValue = "'CINZAS'") val estiloMoldura: String = "CINZAS",
    @ColumnInfo(defaultValue = "''") val biografia: String = "",
    @ColumnInfo(defaultValue = "'EQUILIBRIO'") val focoTreino: String = "EQUILIBRIO",
    @ColumnInfo(defaultValue = "'ESPADA'") val seloPerfil: String = "ESPADA",
)

/**
 * One logged set. [pesoKg], [reps] and [segundos] are nullable because an exercise measures
 * itself in only one of them — load, repetitions or time under tension.
 */
@Entity(
    tableName = "series",
    indices = [Index("exercicioId"), Index("epochDay"), Index("musculo")],
)
data class SerieRegistro(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val exercicioId: String,
    val musculo: String,
    val pesoKg: Float?,
    val reps: Int?,
    val segundos: Int?,
    val score: Float,
    val xpGanho: Int,
    val epochDay: Long,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "1") val valido: Boolean = true,
    @ColumnInfo(defaultValue = "''") val esforco: String = "",
    @ColumnInfo(defaultValue = "''") val equipamento: String = "",
    @ColumnInfo(defaultValue = "'MANUAL'") val origem: String = "MANUAL",
)

/** Additive journey/settings records. Portable backups include these, never body photos. */
@Entity(tableName = "evolution_entry")
data class EvolutionEntry(@PrimaryKey val id: String, val payload: String)

/** [tipo] stays a plain string on purpose — it is written by the engine and read by the UI. */
@Entity(tableName = "missoes", indices = [Index("epochDay")])
data class MissaoDiaria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val tipo: String,
    val descricao: String,
    val alvoMusculo: String?,
    val alvoQtd: Int,
    val progresso: Int = 0,
    val concluida: Boolean = false,
    val xpRecompensa: Int,
)

@Entity(tableName = "conquistas")
data class ConquistaDesbloqueada(
    @PrimaryKey val id: String,
    val desbloqueadaEm: Long,
)

/** Camera-confirmed damage and the unsaved workout set, kept across app restarts. */
@Entity(tableName = "camera_boss")
data class CameraBossProgress(
    @PrimaryKey val bossId: String,
    val stage: Int = 0,
    val damage: Int = 0,
    val wins: Int = 0,
    val pendingReps: Int = 0,
    val lastSessionId: String = "",
    val lastSessionReps: Int = 0,
    @ColumnInfo(defaultValue = "1") val trialRank: Int = 1,
    @ColumnInfo(defaultValue = "0") val bestTrialRank: Int = 0,
    @ColumnInfo(defaultValue = "0") val deaths: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastSessionWins: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastSessionDeaths: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastSessionRevision: Int = 0,
)

/** One actual camera attempt; damage can reset without joining two attempts into one PR. */
@Entity(tableName = "camera_validated_set")
data class CameraValidatedSet(
    @PrimaryKey val id: String,
    val bossId: String,
    val reps: Int,
    val closed: Boolean,
    val completedAt: Long,
    val registered: Boolean = false,
)

@Entity(tableName = "temporada_resumo")
data class TemporadaResumo(
    @PrimaryKey val numero: Int,
    val patentePico: Int,
    val powerScorePico: Float,
    val pontosLigaPico: Int,
    val encerradaEm: Long,
)

/** One row per day, so the power curve survives even after the sets are pruned. */
@Entity(tableName = "power_snapshot")
data class PowerSnapshot(
    @PrimaryKey val epochDay: Long,
    val powerScore: Float,
    val patente: Int,
    val pontosLiga: Int,
)

@Entity(tableName = "preferencias")
data class Preferencias(
    @PrimaryKey val id: Int = 1,
    val objetivo: String,
    val experiencia: String,
    val localPreferido: String,
    val diasPorSemana: Int,
    val descansoPadraoSeg: Int,
    val metaProteinaG: Int,
    val rotinaAtiva: String?,
    val lembretesAtivos: Boolean,
    @ColumnInfo(defaultValue = "''") val condicoes: String = "",
    @ColumnInfo(defaultValue = "'QUALQUER'") val horarioTreino: String = "QUALQUER",
    @ColumnInfo(defaultValue = "0") val metaCalorias: Int = 0,
    val tituloEquipado: String? = null,
    @ColumnInfo(defaultValue = "0") val metaCarboG: Int = 0,
    @ColumnInfo(defaultValue = "0") val metaGorduraG: Int = 0,
    @ColumnInfo(defaultValue = "0") val metasNutricaoManuais: Boolean = false,
    @ColumnInfo(defaultValue = "'LEVE'") val atividadeNutri: String = "LEVE",
    @ColumnInfo(defaultValue = "'MANTER'") val objetivoNutri: String = "MANTER",
)

@Entity(tableName = "refeicoes", indices = [Index("epochDay"), Index(value = ["registroId"], unique = true)])
data class Refeicao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val nome: String,
    val proteinaG: Float,
    val calorias: Float,
    val carboG: Float,
    val gorduraG: Float,
    val porcaoG: Float,
    val barcode: String?,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "'OUTROS'") val categoria: String = "OUTROS",
    @ColumnInfo(defaultValue = "'Registro anterior'") val fonte: String = "Registro anterior",
    @ColumnInfo(defaultValue = "0") val estimado: Boolean = false,
    val fibraG: Float? = null,
    @ColumnInfo(defaultValue = "0") val macrosCompletos: Boolean = false,
    val registroId: String? = null,
)

/** Custom routine. The item list is stored as JSON — see `domain/Rotina.kt`. */
@Entity(tableName = "rotina")
data class RotinaCustom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nome: String,
    val itensJson: String,
    val criadoEm: Long,
)

@Entity(tableName = "treino_sessao", indices = [Index("epochDay"), Index(value = ["sessionUuid"], unique = true)])
data class TreinoSessao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val epochDay: Long,
    val nome: String,
    val xpGanho: Int,
    val series: Int,
    val exercicios: Int,
    val recordes: Int,
    val duracaoSeg: Int,
    val encerradaEm: Long,
    @ColumnInfo(defaultValue = "''") val itensJson: String = "",
    val sessionUuid: String? = null,
    @ColumnInfo(defaultValue = "''") val receiptJson: String = "",
)

@Entity(tableName = "circle_delivery", indices = [Index("userId")])
data class CircleDelivery(@PrimaryKey val id: String, val userId: String, val circleId: String,
    val sessionId: String, val receipt: String, val action: String, val status: String, val error: String?, val createdAt: Long)

@Entity(tableName = "peso_corporal")
data class PesoCorporal(
    @PrimaryKey val epochDay: Long,
    val pesoKg: Float,
    val timestamp: Long,
)

// ---------------------------------------------------------------------------
// Query projections
// ---------------------------------------------------------------------------

data class MelhorScore(val exercicioId: String, val melhor: Float)

data class RecordeExercicio(val exercicioId: String, val pesoKg: Float?, val reps: Int?, val segundos: Int?, val epochDay: Long, val melhorScore: Float, val series: Int)

data class VolumeMusculo(val musculo: String, val series: Int)

data class UltimoTreinoMusculo(val musculo: String, val ultimoDia: Long)

data class TotalNutricao(val proteinaG: Float, val calorias: Float, val carboG: Float = 0f, val gorduraG: Float = 0f)

@Entity(tableName = "agua", indices = [Index("epochDay")])
data class AguaRegistro(@PrimaryKey(autoGenerate = true) val id: Long = 0, val epochDay: Long, val ml: Int, val timestamp: Long)

@Entity(tableName = "alimentos_salvos")
data class AlimentoSalvo(@PrimaryKey val id: String, val nome: String, val alimentoJson: String, val porcaoG: Float)

// ---------------------------------------------------------------------------
// DAO
// ---------------------------------------------------------------------------

@Dao
interface AppDao {
    @Query("SELECT * FROM cardio_session WHERE status != 'FINISHED' LIMIT 1")
    fun activeCardioFlow(): Flow<CardioSession?>
    @Query("SELECT * FROM cardio_session WHERE status != 'FINISHED' LIMIT 1")
    suspend fun activeCardio(): CardioSession?
    @Query("SELECT * FROM cardio_session WHERE id = :id")
    suspend fun cardioById(id: String): CardioSession?
    @Query("SELECT * FROM cardio_session WHERE status = 'FINISHED' ORDER BY endedAt DESC")
    fun cardioHistory(): Flow<List<CardioSession>>
    @Query("SELECT * FROM cardio_session WHERE status = 'FINISHED' ORDER BY endedAt DESC")
    suspend fun allCardio(): List<CardioSession>
    @Query("SELECT COALESCE(SUM(xpGained), 0) FROM cardio_session WHERE status = 'FINISHED' AND epochDay = :day")
    suspend fun cardioXp(day: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCardio(row: CardioSession)
    @Query("DELETE FROM cardio_session WHERE id = :id AND status != 'FINISHED'")
    suspend fun discardCardio(id: String)
    @Query("DELETE FROM cardio_session")
    suspend fun clearCardio()

    @Query("SELECT * FROM evolution_entry ORDER BY id")
    fun evolutionFlow(): Flow<List<EvolutionEntry>>
    @Query("SELECT * FROM evolution_entry ORDER BY id")
    suspend fun evolutionEntries(): List<EvolutionEntry>
    @Query("SELECT * FROM missoes ORDER BY epochDay, id") suspend fun allMissions(): List<MissaoDiaria>
    @Query("SELECT * FROM conquistas ORDER BY desbloqueadaEm") suspend fun allAchievements(): List<ConquistaDesbloqueada>
    @Query("SELECT * FROM power_snapshot ORDER BY epochDay") suspend fun allSnapshots(): List<PowerSnapshot>
    @Query("SELECT * FROM temporada_resumo ORDER BY numero") suspend fun allSeasons(): List<TemporadaResumo>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveEvolution(row: EvolutionEntry)
    @Query("DELETE FROM evolution_entry")
    suspend fun clearEvolution()
    @Query("SELECT * FROM series WHERE valido = 1 ORDER BY timestamp, id")
    fun validHistory(): Flow<List<SerieRegistro>>

    @Query("SELECT * FROM camera_boss")
    fun cameraBossesFlow(): Flow<List<CameraBossProgress>>

    @Query("SELECT * FROM camera_boss WHERE bossId = :id")
    suspend fun cameraBoss(id: String): CameraBossProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarCameraBoss(progress: CameraBossProgress)

    @Query("SELECT * FROM camera_boss")
    suspend fun todosCameraBosses(): List<CameraBossProgress>

    @Query("DELETE FROM camera_boss")
    suspend fun apagarCameraBosses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarCameraSet(row: CameraValidatedSet)

    @Query("SELECT * FROM camera_validated_set WHERE id = :id")
    suspend fun cameraSet(id: String): CameraValidatedSet?

    @Query("SELECT * FROM camera_validated_set WHERE bossId = :bossId AND registered = 0 ORDER BY completedAt, id")
    suspend fun cameraSetsPendentes(bossId: String): List<CameraValidatedSet>

    @Query("SELECT * FROM camera_validated_set WHERE registered = 0")
    suspend fun todosCameraSetsPendentes(): List<CameraValidatedSet>

    @Query("DELETE FROM camera_validated_set")
    suspend fun apagarCameraSets()

    // --- perfil ---

    @Query("SELECT * FROM perfil WHERE id = 1")
    fun perfilFlow(): Flow<Perfil?>

    @Query("SELECT * FROM perfil WHERE id = 1")
    suspend fun perfil(): Perfil?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarPerfil(perfil: Perfil)

    @Query("UPDATE perfil SET pesoCorporalKg = :pesoKg WHERE id = 1")
    suspend fun atualizarPesoCorporal(pesoKg: Float)

    @Query("UPDATE perfil SET nome = :nome, lema = :lema, classe = :classe, estiloMoldura = :estilo, biografia = :biografia, focoTreino = :foco, seloPerfil = :selo WHERE id = 1")
    suspend fun atualizarIdentidade(nome: String, lema: String, classe: String, estilo: String, biografia: String, foco: String, selo: String)

    // --- séries ---

    @Insert
    suspend fun inserirSerie(serie: SerieRegistro): Long

    @Query("SELECT MAX(timestamp) FROM series")
    suspend fun ultimaSerieTimestamp(): Long?

    @Query("SELECT * FROM series ORDER BY timestamp DESC LIMIT :limite")
    fun ultimasSeries(limite: Int): Flow<List<SerieRegistro>>

    @Query("SELECT * FROM series WHERE epochDay = :dia ORDER BY timestamp DESC")
    fun seriesDoDia(dia: Long): Flow<List<SerieRegistro>>

    @Query("SELECT * FROM series WHERE exercicioId = :exercicioId ORDER BY timestamp DESC LIMIT :limite")
    fun seriesDoExercicio(exercicioId: String, limite: Int): Flow<List<SerieRegistro>>

    @Query("SELECT * FROM series WHERE epochDay >= :desde")
    fun seriesDesde(desde: Long): Flow<List<SerieRegistro>>

    @Query("SELECT exercicioId, MAX(score) AS melhor FROM series WHERE valido = 1 GROUP BY exercicioId")
    fun melhoresScores(): Flow<List<MelhorScore>>

    // A real logged set wins by load, then reps/time, then most recent date.
    // Best rank score is independent: it must never masquerade as actual kilograms.
    @Query("""SELECT s.exercicioId, s.pesoKg, s.reps, s.segundos, s.epochDay, g.melhorScore, g.series
        FROM (SELECT exercicioId, MAX(score) AS melhorScore, COUNT(*) AS series
          FROM series WHERE valido = 1 GROUP BY exercicioId) g
        JOIN series s ON s.id = (SELECT t.id FROM series t WHERE t.exercicioId = g.exercicioId AND t.valido = 1
          ORDER BY COALESCE(t.pesoKg, 0) DESC, COALESCE(t.reps, t.segundos, 0) DESC, t.id DESC LIMIT 1)
        ORDER BY s.exercicioId""")
    fun recordesExercicios(): Flow<List<RecordeExercicio>>

    @Query("SELECT MAX(score) FROM series WHERE exercicioId = :exercicioId AND valido = 1")
    suspend fun melhorScoreDe(exercicioId: String): Float?

    // Match the ranking score, not the heaviest load (which can be a different set).
    @Query("SELECT * FROM series WHERE exercicioId = :exercicioId AND valido = 1 ORDER BY score DESC, id DESC LIMIT 1")
    suspend fun melhorSerieDe(exercicioId: String): SerieRegistro?

    @Query("SELECT musculo, COUNT(*) AS series FROM series WHERE valido = 1 GROUP BY musculo")
    fun volumePorMusculo(): Flow<List<VolumeMusculo>>

    @Query("SELECT COUNT(*) FROM series WHERE valido = 1")
    suspend fun totalSeries(): Long

    @Query("SELECT COUNT(*) FROM series WHERE valido = 1")
    fun totalSeriesFlow(): Flow<Long>

    @Query("SELECT COUNT(DISTINCT musculo) FROM series WHERE valido = 1")
    suspend fun musculosTreinados(): Int

    @Query("SELECT COUNT(*) FROM series WHERE epochDay = :dia")
    suspend fun seriesNoDia(dia: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE epochDay = :dia AND musculo = :musculo")
    suspend fun seriesNoDiaDoMusculo(dia: Long, musculo: String): Int

    @Query("SELECT COUNT(*) FROM series WHERE exercicioId = :exercicioId AND epochDay = :dia AND valido = 1")
    suspend fun seriesValidasHojeExercicio(exercicioId: String, dia: Long): Int

    @Query("SELECT musculo, MAX(epochDay) AS ultimoDia FROM series WHERE valido = 1 GROUP BY musculo")
    fun ultimoTreinoPorMusculo(): Flow<List<UltimoTreinoMusculo>>

    // --- missões ---

    @Insert
    suspend fun inserirMissoes(missoes: List<MissaoDiaria>)

    @Update
    suspend fun atualizarMissao(missao: MissaoDiaria)

    @Query("SELECT * FROM missoes WHERE epochDay = :dia AND tipo != 'SYS_WEEK_ACTIVITY' ORDER BY id")
    fun missoesDoDia(dia: Long): Flow<List<MissaoDiaria>>

    @Query("SELECT COUNT(*) FROM missoes WHERE epochDay = :dia AND tipo != 'SYS_WEEK_ACTIVITY'")
    suspend fun contarMissoesDoDia(dia: Long): Int

    @Query("SELECT * FROM missoes WHERE epochDay = :dia AND concluida = 0 AND tipo != 'SYS_WEEK_ACTIVITY'")
    suspend fun missoesAbertasDoDia(dia: Long): List<MissaoDiaria>

    @Query("SELECT * FROM missoes WHERE epochDay = :week AND tipo = 'SYS_WEEK_ACTIVITY' ORDER BY id")
    fun systemWeek(week: Long): Flow<List<MissaoDiaria>>
    @Query("SELECT COUNT(*) FROM missoes WHERE epochDay = :week AND tipo = 'SYS_WEEK_ACTIVITY'")
    suspend fun systemWeekCount(week: Long): Int
    @Query("SELECT * FROM missoes WHERE concluida = 0 AND (epochDay = :day OR (epochDay = :week AND tipo = 'SYS_WEEK_ACTIVITY'))")
    suspend fun systemOpen(day: Long, week: Long): List<MissaoDiaria>
    @Query("SELECT * FROM missoes WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, id DESC")
    fun systemHistory(from: Long, to: Long): Flow<List<MissaoDiaria>>
    @Query("DELETE FROM missoes WHERE epochDay = :day AND tipo != 'SYS_WEEK_ACTIVITY' AND concluida = 0")
    suspend fun replaceUnstartedOrders(day: Long)
    @Query("SELECT * FROM series WHERE epochDay BETWEEN :from AND :to AND valido = 1")
    suspend fun systemSets(from: Long, to: Long): List<SerieRegistro>
    @Query("SELECT * FROM cardio_session WHERE epochDay BETWEEN :from AND :to AND status = 'FINISHED'")
    suspend fun systemCardio(from: Long, to: Long): List<CardioSession>
    @Query("SELECT COUNT(*) FROM agua WHERE epochDay = :day")
    suspend fun systemWater(day: Long): Int
    @Query("SELECT * FROM missoes WHERE epochDay = :day AND tipo != 'SYS_WEEK_ACTIVITY' ORDER BY id")
    suspend fun systemDay(day: Long): List<MissaoDiaria>

    @Query("SELECT COUNT(*) FROM missoes WHERE concluida = 1")
    suspend fun totalMissoesConcluidas(): Int

    @Query("SELECT COUNT(*) FROM missoes WHERE concluida = 1")
    fun totalMissoesConcluidasFlow(): Flow<Int>

    // --- conquistas ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun desbloquearConquista(c: ConquistaDesbloqueada): Long

    @Query("SELECT * FROM conquistas")
    fun conquistasFlow(): Flow<List<ConquistaDesbloqueada>>

    @Query("SELECT id FROM conquistas")
    suspend fun idsConquistas(): List<String>

    // --- competitivo ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarSnapshot(s: PowerSnapshot)

    @Query("SELECT * FROM power_snapshot WHERE epochDay <= :dia ORDER BY epochDay DESC LIMIT 1")
    suspend fun snapshotAteDia(dia: Long): PowerSnapshot?

    @Query("SELECT * FROM power_snapshot ORDER BY epochDay DESC LIMIT :limite")
    fun snapshotsRecentes(limite: Int): Flow<List<PowerSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTemporadaResumo(t: TemporadaResumo)

    @Query("SELECT * FROM temporada_resumo WHERE numero = :numero")
    suspend fun temporadaResumo(numero: Int): TemporadaResumo?

    @Query("SELECT * FROM temporada_resumo WHERE numero = :numero")
    fun temporadaResumoFlow(numero: Int): Flow<TemporadaResumo?>

    @Query("SELECT * FROM temporada_resumo WHERE encerradaEm > 0 ORDER BY numero DESC")
    fun temporadasEncerradas(): Flow<List<TemporadaResumo>>

    // --- preferências ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarPreferencias(p: Preferencias)

    @Query("SELECT * FROM preferencias WHERE id = 1")
    fun preferenciasFlow(): Flow<Preferencias?>

    @Query("SELECT * FROM preferencias WHERE id = 1")
    suspend fun preferencias(): Preferencias?

    @Query("UPDATE perfil SET idade = :idade, alturaCm = :altura, sexo = :sexo WHERE id = 1")
    suspend fun atualizarDadosNutri(idade: Int, altura: Int, sexo: String)

    // --- nutrição ---

    @Insert
    suspend fun inserirRefeicao(r: Refeicao): Long

    @Delete
    suspend fun apagarRefeicao(r: Refeicao)

    @Update suspend fun atualizarRefeicao(r: Refeicao)

    @Query("SELECT * FROM refeicoes WHERE epochDay BETWEEN :inicio AND :fim ORDER BY timestamp DESC")
    fun refeicoesPeriodo(inicio: Long, fim: Long): Flow<List<Refeicao>>

    @Insert suspend fun inserirRefeicoes(rows: List<Refeicao>)
    @Query("SELECT COUNT(*) FROM refeicoes WHERE registroId IN (:ids)") suspend fun contarRegistrosNutri(ids: List<String>): Int

    @Query("SELECT * FROM alimentos_salvos ORDER BY nome") fun alimentosSalvos(): Flow<List<AlimentoSalvo>>
    @Query("SELECT * FROM alimentos_salvos") suspend fun todosAlimentosSalvos(): List<AlimentoSalvo>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun salvarAlimento(row: AlimentoSalvo)
    @Delete suspend fun apagarAlimento(row: AlimentoSalvo)
    @Query("DELETE FROM alimentos_salvos") suspend fun apagarAlimentosSalvos()

    @Query("SELECT * FROM agua WHERE epochDay = :dia ORDER BY timestamp DESC, id DESC") fun aguaDoDia(dia: Long): Flow<List<AguaRegistro>>
    @Query("SELECT * FROM agua") suspend fun todaAgua(): List<AguaRegistro>
    @Insert suspend fun inserirAgua(row: AguaRegistro)
    @Query("DELETE FROM agua WHERE id = (SELECT id FROM agua WHERE epochDay = :dia ORDER BY timestamp DESC, id DESC LIMIT 1)") suspend fun desfazerAgua(dia: Long)
    @Query("DELETE FROM agua") suspend fun apagarAgua()

    @Query("SELECT * FROM refeicoes WHERE epochDay = :dia ORDER BY timestamp DESC")
    fun refeicoesDoDia(dia: Long): Flow<List<Refeicao>>

    @Query(
        "SELECT COALESCE(SUM(proteinaG),0) AS proteinaG, COALESCE(SUM(calorias),0) AS calorias, COALESCE(SUM(carboG),0) AS carboG, COALESCE(SUM(gorduraG),0) AS gorduraG " +
            "FROM refeicoes WHERE epochDay = :dia"
    )
    fun totalNutricaoDoDia(dia: Long): Flow<TotalNutricao>

    // --- rotinas ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarRotina(r: RotinaCustom): Long

    @Query("SELECT * FROM rotina ORDER BY criadoEm DESC")
    fun rotinasFlow(): Flow<List<RotinaCustom>>

    @Query("DELETE FROM rotina WHERE id = :id")
    suspend fun apagarRotina(id: Long)

    // --- sessões ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun salvarSessao(s: TreinoSessao): Long

    @Query("SELECT * FROM treino_sessao WHERE sessionUuid = :uuid LIMIT 1")
    suspend fun sessaoPorUuid(uuid: String): TreinoSessao?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCircleDelivery(row: CircleDelivery): Long

    @Query("SELECT * FROM circle_delivery WHERE userId = :uid ORDER BY createdAt DESC")
    fun circleDeliveries(uid: String): Flow<List<CircleDelivery>>

    @Query("SELECT * FROM circle_delivery WHERE userId = :uid AND status = 'pending' ORDER BY createdAt LIMIT 40")
    suspend fun pendingCircleDeliveries(uid: String): List<CircleDelivery>

    @Query("UPDATE circle_delivery SET status = :status, error = :error WHERE id = :id AND action = :action AND status = 'pending'")
    suspend fun markCircleDelivery(id: String, action: String, status: String, error: String?)

    @Query("UPDATE circle_delivery SET status = 'pending', error = NULL WHERE id = :id AND userId = :uid AND status = 'failed'")
    suspend fun retryCircleDelivery(id: String, uid: String)

    @Query("UPDATE circle_delivery SET action = 'DELETE', status = 'pending', error = NULL WHERE id = :id AND userId = :uid")
    suspend fun withdrawCircleDelivery(id: String, uid: String)

    @Query("DELETE FROM circle_delivery")
    suspend fun clearCircleDeliveries()

    @Query("SELECT * FROM treino_sessao ORDER BY encerradaEm DESC LIMIT :limite")
    fun sessoesRecentes(limite: Int): Flow<List<TreinoSessao>>

    @Query("SELECT * FROM treino_sessao ORDER BY encerradaEm DESC, id DESC")
    fun arquivoSessoes(): Flow<List<TreinoSessao>>

    @Query("SELECT * FROM series ORDER BY epochDay DESC, timestamp, id")
    fun arquivoSeries(): Flow<List<SerieRegistro>>

    @Query("SELECT COUNT(*) FROM treino_sessao")
    fun totalSessoes(): Flow<Int>

    // --- peso corporal ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarPeso(p: PesoCorporal)

    @Query("SELECT * FROM peso_corporal WHERE epochDay >= :desde ORDER BY epochDay ASC")
    fun pesoDesde(desde: Long): Flow<List<PesoCorporal>>

    @Query("SELECT * FROM peso_corporal ORDER BY epochDay DESC LIMIT 1")
    suspend fun ultimoPeso(): PesoCorporal?

    // --- exportação ---

    @Query("SELECT * FROM series ORDER BY timestamp ASC")
    suspend fun todasSeries(): List<SerieRegistro>

    @Query("SELECT * FROM treino_sessao ORDER BY encerradaEm ASC")
    suspend fun todasSessoes(): List<TreinoSessao>

    @Query("SELECT * FROM peso_corporal ORDER BY epochDay ASC")
    suspend fun todosPesos(): List<PesoCorporal>

    @Query("SELECT * FROM refeicoes ORDER BY timestamp ASC")
    suspend fun todasRefeicoes(): List<Refeicao>

    @Query("SELECT * FROM rotina ORDER BY criadoEm ASC")
    suspend fun todasRotinas(): List<RotinaCustom>

    // --- reset ---

    @Query("DELETE FROM perfil")
    suspend fun apagarPerfil()

    @Query("DELETE FROM series")
    suspend fun apagarSeries()

    @Query("DELETE FROM missoes")
    suspend fun apagarMissoes()

    @Query("DELETE FROM conquistas")
    suspend fun apagarConquistas()

    @Query("DELETE FROM power_snapshot")
    suspend fun apagarSnapshots()

    @Query("DELETE FROM temporada_resumo")
    suspend fun apagarTemporadas()

    @Query("DELETE FROM refeicoes")
    suspend fun apagarRefeicoes()

    @Query("DELETE FROM rotina")
    suspend fun apagarRotinas()

    @Query("DELETE FROM treino_sessao")
    suspend fun apagarSessoes()

    @Query("DELETE FROM peso_corporal")
    suspend fun apagarPesos()

    @Query("DELETE FROM preferencias")
    suspend fun apagarPreferencias()

    /** Wipes every table in one shot. Used by the "apagar tudo" action in the settings screen. */
    @Transaction
    suspend fun resetTotal() {
        clearCardio()
        clearEvolution()
        clearCircleDeliveries()
        apagarCameraSets()
        apagarCameraBosses()
        apagarPerfil()
        apagarSeries()
        apagarMissoes()
        apagarConquistas()
        apagarSnapshots()
        apagarTemporadas()
        apagarRefeicoes()
        apagarAgua()
        apagarAlimentosSalvos()
        apagarRotinas()
        apagarSessoes()
        apagarPesos()
        apagarPreferencias()
    }
}

// ---------------------------------------------------------------------------
// Database
// ---------------------------------------------------------------------------

@Database(
    entities = [
        Perfil::class,
        SerieRegistro::class,
        MissaoDiaria::class,
        ConquistaDesbloqueada::class,
        CameraBossProgress::class,
        CameraValidatedSet::class,
        TemporadaResumo::class,
        PowerSnapshot::class,
        Preferencias::class,
        Refeicao::class,
        AguaRegistro::class,
        AlimentoSalvo::class,
        RotinaCustom::class,
        TreinoSessao::class,
        PesoCorporal::class,
        EvolutionEntry::class,
        CircleDelivery::class,
        CardioSession::class,
    ],
    version = 15,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao
}
