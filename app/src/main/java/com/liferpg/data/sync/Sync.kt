package com.liferpg.data.sync

// NOTE: in the original build `object SupabaseConfig` lived here too. It is kept in
// SupabaseConfig.kt (gitignored, see SupabaseConfig.kt.example) so the live project URL
// and anon key never enter version control. Nothing else about it changed.

data class RankingGlobal(
    val posicao: Int,
    val apelido: String,
    val powerScore: Float,
    val patente: Int,
    val userId: String = "",
    val perfilPublicado: Boolean = true,
) {
    val temPower: Boolean get() = powerScore > 0f
    /** Initial enrollment can be named by onboarding; a real save must not be overwritten silently. */
    fun protegePublicacaoLocal(nickname: String, localPower: Float): Boolean =
        perfilPublicado && (!apelido.equals(nickname, ignoreCase = true) || (temPower && localPower == 0f))
}

data class RankingPublicacao(
    val apelido: String,
    val powerScore: Float,
    val patente: Int,
    val divisao: Int?,
    val lp: Int,
    val temporada: Int,
)

data class AmizadeArena(
    val id: String,
    val jogadorId: String,
    val apelido: String,
    val powerScore: Float,
    val patente: Int,
    val status: String,
    val enviadaPorMim: Boolean,
)

data class TreinoAmigo(val nome: String, val dia: Long, val series: Int, val exercicios: Int = 0, val recordes: Int = 0, val duracaoSeg: Int = 0, val itens: List<com.liferpg.domain.ItemRotinaDef> = emptyList())
data class RotinaAmigo(val nome: String, val exercicios: List<String>, val diasSemana: List<Int>, val itens: List<com.liferpg.domain.ItemRotinaDef> = emptyList())
data class IdentidadeAmigo(val lema: String = "", val biografia: String = "", val classe: String = "", val moldura: String = "CINZAS", val foco: String = "EQUILIBRIO", val selo: String = "ESPADA")
data class RecordeAmigo(
    val exercicioId: String, val nome: String, val musculo: String, val modo: String,
    val pesoKg: Float?, val reps: Int, val segundos: Int, val dia: Long,
    val patente: String, val series: Int,
)
data class PatenteAmigo(val musculo: String, val patente: String)
data class PerfilAmigo(
    val level: Int,
    val streak: Int,
    val bestStreak: Int,
    val totalSets: Int,
    val totalSessions: Int,
    val routines: List<RotinaAmigo>,
    val recentWorkouts: List<TreinoAmigo>,
    val muscleRanks: List<PatenteAmigo>,
    val identity: IdentidadeAmigo = IdentidadeAmigo(),
    val records: List<RecordeAmigo> = emptyList(),
    val updatedAt: String = "",
    val evolution: FriendEvolution? = null,
)

data class DesafioArena(
    val id: String,
    val oponenteId: String,
    val oponente: String,
    val euDesafiei: Boolean,
    val status: String,
    val duracaoHoras: Int,
    val metaPower: Float,
    val meuInicio: Float,
    val inicioOponente: Float,
    val meuPower: Float,
    val powerOponente: Float,
    val terminaEm: String?,
    val vencedorId: String?,
) {
    val meuGanho: Float get() = (meuPower - meuInicio).coerceAtLeast(0f)
    val ganhoOponente: Float get() = (powerOponente - inicioOponente).coerceAtLeast(0f)
}

interface SyncService {
    suspend fun conversaCirculo(circle:String,post:String,before:CircleComment?=null):Result<CircleConversation> = Result.failure(CircleRequestFailure("Conversa indisponível.",false))
    suspend fun reagirCirculo(post:String,kind:CircleReaction?):Result<CircleSocial> = Result.failure(CircleRequestFailure("Reação indisponível.",false))
    suspend fun comentarCirculo(post:String,id:String,body:String):Result<Unit> = Result.failure(CircleRequestFailure("Comentário indisponível.",false))
    suspend fun excluirComentarioCirculo(id:String):Result<Unit> = Result.failure(CircleRequestFailure("Comentário indisponível.",false))
    suspend fun avisosCirculo():Result<List<CircleCommentNotice>> = Result.success(emptyList())
    suspend fun lerComentariosCirculo(post:String,ids:List<String>):Result<Unit> = Result.failure(CircleRequestFailure("Conversa indisponível.",false))
    suspend fun circulos(): Result<List<TrainingCircle>> = Result.failure(CircleRequestFailure("Círculos indisponíveis nesta instalação.", false))
    suspend fun placarCirculo(id: String, week: String? = null): Result<CircleWeek> = Result.failure(CircleRequestFailure("Placar detalhado indisponível nesta instalação.", false))
    suspend fun criarCirculo(name: String, goal: Int, timezone: String): Result<Unit> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    suspend fun convidarCirculo(id: String, target: String): Result<Unit> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    suspend fun responderCirculo(id: String, accept: Boolean, goal: Int): Result<Unit> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    suspend fun sairCirculo(id: String): Result<Unit> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    suspend fun recibosCirculo(id: String, before: CirclePost? = null): Result<List<CirclePost>> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    suspend fun enviarReciboCirculo(id: String, session: String, receipt: String, actor: String): Result<Unit> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    suspend fun retirarReciboCirculo(id: String, session: String, actor: String): Result<Unit> = Result.failure(CircleRequestFailure("Círculos indisponíveis.", false))
    val habilitado: Boolean
    suspend fun pactos(): Result<List<TrainingPact>> = Result.failure(IllegalStateException("Pactos indisponíveis nesta instalação."))
    suspend fun criarPacto(name: String, boss: String, targets: List<String>, goal: Int): Result<Unit> = Result.failure(IllegalStateException("Pactos indisponíveis."))
    suspend fun responderPacto(id: String, accept: Boolean, goal: Int): Result<Unit> = Result.failure(IllegalStateException("Pactos indisponíveis."))
    suspend fun iniciarPacto(id: String): Result<Unit> = Result.failure(IllegalStateException("Pactos indisponíveis."))
    suspend fun contribuirPacto(id: String, day: Long, source: String): Result<Unit> = Result.failure(IllegalStateException("Pactos indisponíveis."))
    suspend fun cancelarPacto(id: String): Result<Unit> = Result.failure(IllegalStateException("Pactos indisponíveis."))
    suspend fun publicarEvolucao(body: String): Result<Unit> = Result.failure(IllegalStateException("Evolução indisponível."))
    suspend fun verEvolucao(id: String): Result<FriendEvolution?> = Result.success(null)
    suspend fun lerBackup(): Result<CloudBackup?> = Result.failure(IllegalStateException("Backup na nuvem indisponível."))
    suspend fun salvarBackup(body: String, revision: Long): Result<Long> = Result.failure(IllegalStateException("Backup na nuvem indisponível."))
    suspend fun apagarBackup(): Result<Unit> = Result.failure(IllegalStateException("Backup na nuvem indisponível."))

    suspend fun publicarRanking(r: RankingPublicacao): Result<Unit>

    suspend fun leaderboard(temporada: Int, limite: Int = 200): Result<List<RankingGlobal>>

    suspend fun meuRanking(): Result<RankingGlobal?>

    suspend fun buscarCacadores(consulta: String): Result<List<RankingGlobal>>

    suspend fun amizades(): Result<List<AmizadeArena>>

    suspend fun publicarPerfilAmigos(perfil: PerfilAmigo): Result<Unit>

    suspend fun verPerfilAmigo(jogadorId: String): Result<PerfilAmigo?>

    suspend fun solicitarAmizade(jogadorId: String): Result<Unit>

    suspend fun responderAmizade(pedidoId: String, aceitar: Boolean): Result<Unit>

    suspend fun removerAmizade(jogadorId: String): Result<Unit>

    suspend fun desafios(): Result<List<DesafioArena>>

    suspend fun criarDesafio(jogadorId: String, duracaoHoras: Int, metaPower: Float): Result<Unit>

    suspend fun responderDesafio(desafioId: String, aceitar: Boolean): Result<Unit>

    suspend fun cancelarDesafio(desafioId: String): Result<Unit>

    /** Removes everything the signed-in user published remotely (their leaderboard rows). */
    suspend fun apagarMeusDados(): Result<Unit>
}

/** Fallback used when Supabase is not configured: accepts everything, stores nothing. */
class LocalSyncService : SyncService {

    override val habilitado = false

    override suspend fun publicarRanking(r: RankingPublicacao): Result<Unit> =
        Result.success(Unit)

    override suspend fun leaderboard(temporada: Int, limite: Int): Result<List<RankingGlobal>> =
        Result.success(emptyList())

    override suspend fun meuRanking(): Result<RankingGlobal?> = Result.success(null)

    override suspend fun buscarCacadores(consulta: String): Result<List<RankingGlobal>> =
        Result.success(emptyList())

    override suspend fun amizades() = Result.success(emptyList<AmizadeArena>())
    override suspend fun publicarPerfilAmigos(perfil: PerfilAmigo) = Result.success(Unit)
    override suspend fun verPerfilAmigo(jogadorId: String) = Result.success<PerfilAmigo?>(null)
    override suspend fun solicitarAmizade(jogadorId: String) = Result.success(Unit)
    override suspend fun responderAmizade(pedidoId: String, aceitar: Boolean) = Result.success(Unit)
    override suspend fun removerAmizade(jogadorId: String) = Result.success(Unit)
    override suspend fun desafios() = Result.success(emptyList<DesafioArena>())
    override suspend fun criarDesafio(jogadorId: String, duracaoHoras: Int, metaPower: Float) = Result.success(Unit)
    override suspend fun responderDesafio(desafioId: String, aceitar: Boolean) = Result.success(Unit)
    override suspend fun cancelarDesafio(desafioId: String) = Result.success(Unit)

    override suspend fun apagarMeusDados(): Result<Unit> = Result.success(Unit)
}
