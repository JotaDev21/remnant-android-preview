package com.liferpg.data.sync

import com.liferpg.data.net.await
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray

/**
 * Live [SyncService] over PostgREST.
 *
 * - Arena directory requires a confirmed, linked account. Enrollment is independent of Power.
 * - `publicar_ranking` and `apagar_meus_dados` are user operations: they only ever run with the
 *   user's own access token and the server pins every row to `auth.uid()`.
 *
 * Calls are cancellable and bounded by a call timeout; nothing is retried automatically because a
 * publish is not idempotent from the user's point of view.
 */
class SupabaseSyncService(
    private val auth: SupabaseAuth,
    private val url: String = SupabaseConfig.URL,
    private val anonKey: String = SupabaseConfig.ANON_KEY,
) : SyncService {

    override val habilitado = SupabaseConfig.configurado

    private val json = "application/json".toMediaType()
    private val moshi = Moshi.Builder().build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun base() = url.trimEnd('/')

    private suspend fun circleRpc(name: String, body: JSONObject = JSONObject()): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uid = auth.sessao.value?.takeUnless { it.anonimo }?.userId ?: error("Entre na sua conta para continuar.")
            val token = auth.accessTokenValido() ?: error("Entre novamente na sua conta.")
            check(auth.sessao.value?.userId == uid) { "Sua conta mudou. Tente novamente." }
            rpc(name, body.toString(), token).use { response ->
                val content = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw CircleRequestFailure(when {
                    response.code == 404 -> "O servidor dos Círculos precisa ser atualizado."
                    content.contains("circle limit") -> "Este círculo ou esta conta atingiu o limite de participantes/convites."
                    content.contains("receipt outside window") -> "Compartilhe um treino feito após entrar no círculo, com até sete dias de atraso. Confira o relógio do aparelho."
                    content.contains("receipt removed") -> "Este resumo já foi retirado e não pode ser republicado."
                    content.contains("receipt conflict") -> "Este treino já foi publicado com outro resumo. Atualize a atividade."
                    content.contains("receipt rate limit") -> "Você atingiu o limite de resumos neste círculo nas últimas 24 horas."
                    content.contains("accepted ally required") -> "Só é possível convidar aliados com amizade aceita."
                    content.contains("invitation closed") -> "Este convite já foi respondido. Atualize a lista."
                    content.contains("interaction rate limit") -> "Muitas interações seguidas. Aguarde um minuto e tente novamente."
                    content.contains("comment conflict") -> "Este comentário já mudou. Atualize a conversa antes de tentar novamente."
                    content.contains("receipt unavailable") -> "Este treino foi retirado ou você não participa mais do círculo."
                    response.code in listOf(401,403) -> "Acesso encerrado. Confira sua conta e a participação no círculo."
                    response.code in 500..599 || response.code == 429 -> if(name=="ver_placar_circulo") "Servidor indisponível agora. Tente atualizar o placar em instantes." else "Servidor indisponível agora. O envio será tentado novamente."
                    else -> "Não foi possível concluir. Confira os dados e tente novamente."
                }, response.code in 500..599 || response.code == 429,response.code in listOf(401,403))
                check(auth.sessao.value?.userId == uid) { "Sua conta mudou durante a ação." }
                Result.success(content)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(if (e is IOException) CircleRequestFailure("Sem conexão. Tente novamente quando a rede voltar.", true) else e) }
    }
    override suspend fun conversaCirculo(circle:String,post:String,before:CircleComment?) = circleRpc("ver_conversa_circulo",JSONObject().put("p_circle",circle).put("p_post",post).apply {
        before?.let { put("p_before",it.createdAt); put("p_before_id",it.id) }
    }).mapCatching(CircleConversationCodec::conversation)
    override suspend fun reagirCirculo(post:String,kind:CircleReaction?) = circleRpc("reagir_recibo_circulo",JSONObject().put("p_post",post).put("p_kind",kind?.key?:JSONObject.NULL)).mapCatching { CircleConversationCodec.social(JSONObject(it)) }
    override suspend fun comentarCirculo(post:String,id:String,body:String) = circleRpc("comentar_recibo_circulo",JSONObject().put("p_post",post).put("p_id",id).put("p_body",body)).map { Unit }
    override suspend fun excluirComentarioCirculo(id:String) = circleRpc("excluir_comentario_circulo",JSONObject().put("p_comment",id)).map { Unit }
    override suspend fun avisosCirculo() = circleRpc("listar_avisos_circulo").mapCatching(CircleConversationCodec::notices)
    override suspend fun lerComentariosCirculo(post:String,ids:List<String>) = circleRpc("ler_comentarios_circulo",JSONObject().put("p_post",post).put("p_ids",JSONArray(ids))).map { Unit }
    override suspend fun circulos() = circleRpc("listar_circulos").mapCatching(CircleCodec::circles)
    override suspend fun placarCirculo(id: String, week: String?) = circleRpc("ver_placar_circulo",
        JSONObject().put("p_circle", id).put("p_week", week ?: JSONObject.NULL)).mapCatching(CircleWeekCodec::read)
    override suspend fun criarCirculo(name: String, goal: Int, timezone: String) = circleRpc("criar_circulo", JSONObject().put("p_name",name).put("p_goal",goal).put("p_timezone",timezone)).map { Unit }
    override suspend fun convidarCirculo(id: String, target: String) = circleRpc("convidar_circulo",JSONObject().put("p_circle",id).put("p_target",target)).map { Unit }
    override suspend fun responderCirculo(id: String, accept: Boolean, goal: Int) = circleRpc("responder_circulo",JSONObject().put("p_circle",id).put("p_accept",accept).put("p_goal",goal)).map { Unit }
    override suspend fun sairCirculo(id: String) = circleRpc("sair_circulo",JSONObject().put("p_circle",id)).map { Unit }
    override suspend fun recibosCirculo(id: String, before: CirclePost?) = circleRpc("listar_recibos_circulo",JSONObject().put("p_circle",id).apply {
        before?.let { put("p_before",it.createdAt); put("p_before_id",it.id) }
    }).mapCatching(CircleCodec::posts)
    override suspend fun enviarReciboCirculo(id: String, session: String, receipt: String, actor: String) = circleRpc("publicar_recibo_circulo",JSONObject().put("p_circle",id).put("p_session",session).put("p_receipt",JSONObject(receipt)).put("p_actor",actor)).map { Unit }
    override suspend fun retirarReciboCirculo(id: String, session: String, actor: String) = circleRpc("retirar_recibo_circulo",JSONObject().put("p_circle",id).put("p_session",session).put("p_actor",actor)).map { Unit }

    private suspend fun evolutionRpc(name: String, body: JSONObject = JSONObject()): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = auth.accessTokenValido() ?: error("Entre na sua conta para continuar.")
            rpc(name, body.toString(), token).use { response ->
                val result = response.body?.string().orEmpty()
                check(response.isSuccessful) { when {
                    response.code == 404 -> "O servidor ainda precisa da atualização de campanhas, pactos e backups."
                    result.contains("backup conflict") -> "Outro aparelho atualizou o backup. Confira a cópia da nuvem antes de substituir."
                    result.contains("two allies required") -> "Espere pelo menos um aliado aceitar para iniciar."
                    result.contains("invitation closed") || result.contains("pact closed") -> "Este convite ou pacto já encerrou. Atualize a lista."
                    response.code in listOf(401, 403) -> "Sua conta não tem acesso a esta ação. Entre novamente ou confira o convite."
                    else -> "Não consegui concluir esta ação. Tente novamente (HTTP ${response.code})."
                } }
                Result.success(result)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(if (e is IOException) IOException("Sem conexão. Seu progresso local foi mantido.", e) else e) }
    }
    override suspend fun pactos() = evolutionRpc("listar_pactos").mapCatching(EvolutionNetworkCodec::pacts)
    override suspend fun criarPacto(name: String, boss: String, targets: List<String>, goal: Int) = evolutionRpc("criar_pacto",
        JSONObject().put("p_name", name).put("p_boss", boss).put("p_targets", JSONArray(targets)).put("p_goal", goal)).map { Unit }
    override suspend fun responderPacto(id: String, accept: Boolean, goal: Int) = evolutionRpc("responder_pacto",
        JSONObject().put("p_pact", id).put("p_accept", accept).put("p_goal", goal)).map { Unit }
    override suspend fun iniciarPacto(id: String) = evolutionRpc("iniciar_pacto", JSONObject().put("p_pact", id)).map { Unit }
    override suspend fun contribuirPacto(id: String, day: Long, source: String) = evolutionRpc("contribuir_pacto",
        JSONObject().put("p_pact", id).put("p_day", day).put("p_source", source)).map { Unit }
    override suspend fun cancelarPacto(id: String) = evolutionRpc("cancelar_pacto", JSONObject().put("p_pact", id)).map { Unit }
    override suspend fun publicarEvolucao(body: String) = evolutionRpc("publicar_evolucao", JSONObject().put("p_summary", JSONObject(body))).map { Unit }
    override suspend fun verEvolucao(id: String) = evolutionRpc("ver_evolucao_amigo", JSONObject().put("p_target", id)).mapCatching(EvolutionNetworkCodec::friend)
    override suspend fun lerBackup() = evolutionRpc("ler_backup").mapCatching(EvolutionNetworkCodec::backup)
    override suspend fun salvarBackup(body: String, revision: Long) = evolutionRpc("salvar_backup",
        JSONObject().put("p_backup", JSONObject(body)).put("p_expected_revision", revision)).mapCatching { it.trim().toLong() }
    override suspend fun apagarBackup() = evolutionRpc("apagar_backup").map { Unit }

    @JsonClass(generateAdapter = true)
    data class LeaderboardRow(
        val user_id: String?,
        val apelido: String?,
        val power_score: Double?,
        val patente: Int?,
        val perfil_publicado: Boolean? = null,
        val posicao: Int? = null,
    )

    @JsonClass(generateAdapter = true)
    data class FriendshipRow(
        val id: String,
        val player_user_id: String,
        val apelido: String?,
        val power_score: Double?,
        val patente: Int?,
        val status: String,
        val outgoing: Boolean,
    )

    @JsonClass(generateAdapter = true)
    data class ChallengeRow(
        val id: String,
        val opponent_user_id: String,
        val opponent_name: String?,
        val is_challenger: Boolean,
        val status: String,
        val duration_hours: Int,
        val target_delta: Double,
        val my_start: Double,
        val opponent_start: Double,
        val my_current: Double,
        val opponent_current: Double,
        val ends_at: String?,
        val winner_user_id: String?,
    )

    override suspend fun leaderboard(temporada: Int, limite: Int): Result<List<RankingGlobal>> =
        withContext(Dispatchers.IO) {
            rede {
                check(auth.sessao.value?.vinculada == true) { "Entre com Google ou e-mail para acessar a Arena." }
                val bearer = auth.accessTokenValido() ?: error("Entre novamente para acessar a Arena.")
                val body = JSONObject().put("p_temporada", temporada).put("p_limite", limite.coerceIn(1, 200)).put("p_offset", 0)
                rpc("listar_arena", body.toString(), bearer).use { resp ->
                    val corpo = resp.body?.string() ?: ""
                    check(resp.isSuccessful) { "A Arena Global não respondeu (HTTP ${resp.code})." }
                    val tipo = Types.newParameterizedType(List::class.java, LeaderboardRow::class.java)
                    val linhas: List<LeaderboardRow> =
                        moshi.adapter<List<LeaderboardRow>>(tipo).fromJson(corpo) ?: emptyList()
                    linhas.mapIndexed { i, r ->
                        RankingGlobal(
                            posicao = r.posicao ?: if ((r.power_score ?: 0.0) > 0) i + 1 else 0,
                            apelido = r.apelido ?: "Caçador",
                            powerScore = (r.power_score ?: 0.0).toFloat(),
                            patente = r.patente ?: 0,
                            userId = r.user_id ?: "",
                            perfilPublicado = r.perfil_publicado ?: true,
                        )
                    }
                }
            }
        }

    override suspend fun meuRanking(): Result<RankingGlobal?> = withContext(Dispatchers.IO) {
        rede {
            val token = auth.accessTokenValido() ?: error("Entre na Arena Global para consultar seu perfil.")
            val userId = auth.sessao.value?.userId.orEmpty()
            check(Regex("[0-9a-fA-F-]{36}").matches(userId)) { "Identidade da Arena inválida." }
            val req = Request.Builder()
                .url("${base()}/rest/v1/leaderboard?user_id=eq.$userId&limit=1")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).await().use { resp ->
                val body = resp.body?.string().orEmpty()
                check(resp.isSuccessful) { "Não consegui conferir o perfil desta conta (HTTP ${resp.code})." }
                val type = Types.newParameterizedType(List::class.java, LeaderboardRow::class.java)
                moshi.adapter<List<LeaderboardRow>>(type).fromJson(body)?.firstOrNull()?.let { row ->
                    RankingGlobal(0, row.apelido ?: "Caçador", (row.power_score ?: 0.0).toFloat(),
                        row.patente ?: 0, row.user_id.orEmpty(), row.perfil_publicado ?: true)
                }
            }
        }
    }

    override suspend fun publicarRanking(r: RankingPublicacao): Result<Unit> =
        withContext(Dispatchers.IO) {
            rede {
                val token = auth.accessTokenValido()
                    ?: error("Entre na Arena Global para publicar seu ranking.")
                val corpo = "{\"p_apelido\":${quote(r.apelido)}," +
                    "\"p_power\":${r.powerScore}," +
                    "\"p_patente\":${r.patente}," +
                    "\"p_divisao\":${r.divisao ?: "null"}," +
                    "\"p_lp\":${r.lp}," +
                    "\"p_temporada\":${r.temporada}}"
                rpc("publicar_ranking", corpo, token).use { resp ->
                    check(resp.isSuccessful) {
                        when (resp.code) {
                            401, 403 -> "Sua sessão da Arena expirou. Entre novamente."
                            429 -> "Publicação muito frequente. Aguarde alguns segundos."
                            else -> "Não foi possível publicar agora (HTTP ${resp.code})."
                        }
                    }
                }
            }
        }

    override suspend fun buscarCacadores(consulta: String): Result<List<RankingGlobal>> =
        withContext(Dispatchers.IO) {
            rede {
                val token = auth.accessTokenValido() ?: error("Entre na Arena Global para buscar caçadores.")
                rpc("buscar_cacadores", "{\"p_query\":${quote(consulta.trim())}}", token).use { resp ->
                    val corpo = resp.body?.string() ?: ""
                    check(resp.isSuccessful) { mensagemSocial(resp.code, corpo) }
                    val tipo = Types.newParameterizedType(List::class.java, LeaderboardRow::class.java)
                    val linhas: List<LeaderboardRow> = moshi.adapter<List<LeaderboardRow>>(tipo)
                        .fromJson(corpo) ?: emptyList()
                    linhas.map { row ->
                        RankingGlobal(
                            posicao = 0,
                            apelido = row.apelido ?: "Caçador",
                            powerScore = (row.power_score ?: 0.0).toFloat(),
                            patente = row.patente ?: 0,
                            userId = row.user_id.orEmpty(),
                        )
                    }
                }
            }
        }

    override suspend fun amizades(): Result<List<AmizadeArena>> = listaRpc("listar_amizades") { r: FriendshipRow ->
        AmizadeArena(
            id = r.id,
            jogadorId = r.player_user_id,
            apelido = r.apelido ?: "Caçador",
            powerScore = (r.power_score ?: 0.0).toFloat(),
            patente = r.patente ?: 0,
            status = r.status,
            enviadaPorMim = r.outgoing,
        )
    }

    override suspend fun publicarPerfilAmigos(perfil: PerfilAmigo): Result<Unit> =
        withContext(Dispatchers.IO) {
            rede {
                val body = JSONObject().apply {
                    put("p_level", perfil.level)
                    put("p_streak", perfil.streak)
                    put("p_best_streak", perfil.bestStreak)
                    put("p_total_sets", perfil.totalSets)
                    put("p_total_sessions", perfil.totalSessions)
                    put("p_routines", JSONArray().apply {
                        perfil.routines.forEach { routine ->
                            put(JSONObject().put("nome", routine.nome)
                                .put("exercicios", JSONArray(routine.exercicios))
                                .put("dias", JSONArray(routine.diasSemana))
                                .put("itens", JSONArray().apply { routine.itens.forEach { item ->
                                    put(JSONObject().put("id", item.exercicioId).put("series", item.series)
                                        .put("repMin", item.repMin).put("repMax", item.repMax).put("descanso", item.descansoSeg))
                                } }))
                        }
                    })
                    put("p_recent_workouts", JSONArray().apply {
                        perfil.recentWorkouts.forEach { row ->
                            put(JSONObject().put("nome", row.nome).put("dia", row.dia).put("series", row.series)
                                .put("exercicios", row.exercicios).put("recordes", row.recordes).put("duracao", row.duracaoSeg)
                                .put("itens", JSONArray().apply { row.itens.forEach { item ->
                                    put(JSONObject().put("id", item.exercicioId).put("series", item.series)
                                        .put("repMin", item.repMin).put("repMax", item.repMax).put("descanso", item.descansoSeg))
                                } }))
                        }
                    })
                    put("p_muscle_ranks", JSONArray().apply {
                        perfil.muscleRanks.forEach { row ->
                            put(JSONObject().put("musculo", row.musculo).put("patente", row.patente))
                        }
                    })
                    put("p_identity", JSONObject().put("lema", perfil.identity.lema).put("biografia", perfil.identity.biografia)
                        .put("classe", perfil.identity.classe).put("moldura", perfil.identity.moldura)
                        .put("foco", perfil.identity.foco).put("selo", perfil.identity.selo))
                    put("p_records", JSONArray().apply { perfil.records.forEach { record ->
                        put(JSONObject().put("id", record.exercicioId).put("nome", record.nome).put("musculo", record.musculo)
                            .put("modo", record.modo).put("kg", record.pesoKg ?: JSONObject.NULL).put("reps", record.reps)
                            .put("segundos", record.segundos).put("dia", record.dia)
                            .put("patente", record.patente).put("series", record.series))
                    } })
                }
                val token = auth.accessTokenValido() ?: error("Entre na Arena Global para continuar.")
                val v2 = rpc("publicar_perfil_amigos_v2", body.toString(), token)
                if (v2.code == 404) {
                    v2.close()
                    body.remove("p_identity"); body.remove("p_records")
                    body.put("p_routines", JSONArray().apply { perfil.routines.take(7).forEach { r ->
                        put(JSONObject().put("nome", r.nome).put("exercicios", JSONArray(r.exercicios.take(12))).put("dias", JSONArray(r.diasSemana)))
                    } })
                    body.put("p_recent_workouts", JSONArray().apply { perfil.recentWorkouts.take(5).forEach { w ->
                        put(JSONObject().put("nome", w.nome).put("dia", w.dia).put("series", w.series))
                    } })
                    rpc("publicar_perfil_amigos", body.toString(), token).use { legacy ->
                        check(legacy.isSuccessful) { mensagemSocial(legacy.code, legacy.body?.string().orEmpty()) }
                    }
                    error("O resumo foi publicado. O servidor ainda precisa da atualização de PRs entre aliados.")
                }
                v2.use { resp ->
                    check(resp.isSuccessful) { mensagemSocial(resp.code, resp.body?.string().orEmpty()) }
                }
            }
        }

    override suspend fun verPerfilAmigo(jogadorId: String): Result<PerfilAmigo?> =
        withContext(Dispatchers.IO) {
            rede {
                val token = auth.accessTokenValido() ?: error("Entre na Arena Global para continuar.")
                val target = "{\"p_target\":${quote(jogadorId)}}"
                val v2 = rpc("ver_perfil_amigo_v2", target, token)
                val response = if (v2.code == 404) { v2.close(); rpc("ver_perfil_amigo", target, token) } else v2
                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    check(resp.isSuccessful) { mensagemSocial(resp.code, body) }
                    val rows = JSONArray(body)
                    if (rows.length() == 0) null else rows.getJSONObject(0).let { row ->
                        val routines = row.getJSONArray("routines")
                        val workouts = row.getJSONArray("recent_workouts")
                        val ranks = row.getJSONArray("muscle_ranks")
                        val identity = row.optJSONObject("identity") ?: JSONObject()
                        val records = row.optJSONArray("exercise_records") ?: JSONArray()
                        PerfilAmigo(
                            level = row.getInt("level"),
                            streak = row.getInt("streak"),
                            bestStreak = row.getInt("best_streak"),
                            totalSets = row.getInt("total_sets"),
                            totalSessions = row.getInt("total_sessions"),
                            routines = (0 until routines.length()).map { index ->
                                val value = routines.get(index)
                                if (value is JSONObject) {
                                    val exercises = value.optJSONArray("exercicios") ?: JSONArray()
                                    val days = value.optJSONArray("dias") ?: JSONArray()
                                    val items = value.optJSONArray("itens") ?: JSONArray()
                                    RotinaAmigo(
                                        value.optString("nome", "Treino"),
                                        (0 until exercises.length()).map { exercises.getString(it) },
                                        (0 until days.length()).map { days.getInt(it) },
                                        (0 until items.length()).map { i -> items.getJSONObject(i).let { item ->
                                            com.liferpg.domain.ItemRotinaDef(item.getString("id"), item.getInt("series"), item.getInt("repMin"), item.getInt("repMax"), item.getInt("descanso"))
                                        } },
                                    )
                                } else RotinaAmigo(value.toString(), emptyList(), emptyList())
                            },
                            recentWorkouts = (0 until workouts.length()).map {
                                workouts.getJSONObject(it).let { w ->
                                    val items = w.optJSONArray("itens") ?: JSONArray()
                                    TreinoAmigo(w.getString("nome"), w.getLong("dia"), w.getInt("series"), w.optInt("exercicios"), w.optInt("recordes"), w.optInt("duracao"),
                                        (0 until items.length()).map { index -> items.getJSONObject(index).let { item ->
                                            com.liferpg.domain.ItemRotinaDef(item.getString("id"), item.getInt("series"), item.getInt("repMin"), item.getInt("repMax"), item.getInt("descanso"))
                                        } })
                                }
                            },
                            muscleRanks = (0 until ranks.length()).map {
                                ranks.getJSONObject(it).let { r -> PatenteAmigo(r.getString("musculo"), r.getString("patente")) }
                            },
                            identity = IdentidadeAmigo(identity.optString("lema"), identity.optString("biografia"), identity.optString("classe"),
                                identity.optString("moldura", "CINZAS"), identity.optString("foco", "EQUILIBRIO"), identity.optString("selo", "ESPADA")),
                            records = (0 until records.length()).map { i -> records.getJSONObject(i).let { r ->
                                RecordeAmigo(r.getString("id"), r.getString("nome"), r.getString("musculo"), r.getString("modo"),
                                    if (r.isNull("kg")) null else r.getDouble("kg").toFloat(), r.optInt("reps"), r.optInt("segundos"),
                                    r.getLong("dia"), r.optString("patente", "Sem patente"), r.optInt("series"))
                            } },
                            updatedAt = row.optString("updated_at"),
                        )
                    }
                }
            }
        }

    override suspend fun solicitarAmizade(jogadorId: String): Result<Unit> =
        rpcSocial("solicitar_amizade", "{\"p_target\":${quote(jogadorId)}}")

    override suspend fun responderAmizade(pedidoId: String, aceitar: Boolean): Result<Unit> =
        rpcSocial("responder_amizade", "{\"p_request\":${quote(pedidoId)},\"p_accept\":$aceitar}")

    override suspend fun removerAmizade(jogadorId: String): Result<Unit> =
        rpcSocial("remover_amizade", "{\"p_target\":${quote(jogadorId)}}")

    override suspend fun desafios(): Result<List<DesafioArena>> = listaRpc("listar_desafios") { r: ChallengeRow ->
        DesafioArena(
            id = r.id,
            oponenteId = r.opponent_user_id,
            oponente = r.opponent_name ?: "Caçador",
            euDesafiei = r.is_challenger,
            status = r.status,
            duracaoHoras = r.duration_hours,
            metaPower = r.target_delta.toFloat(),
            meuInicio = r.my_start.toFloat(),
            inicioOponente = r.opponent_start.toFloat(),
            meuPower = r.my_current.toFloat(),
            powerOponente = r.opponent_current.toFloat(),
            terminaEm = r.ends_at,
            vencedorId = r.winner_user_id,
        )
    }

    override suspend fun criarDesafio(jogadorId: String, duracaoHoras: Int, metaPower: Float): Result<Unit> =
        rpcSocial(
            "criar_desafio",
            "{\"p_target\":${quote(jogadorId)},\"p_duration_hours\":$duracaoHoras,\"p_target_delta\":$metaPower}",
        )

    override suspend fun responderDesafio(desafioId: String, aceitar: Boolean): Result<Unit> =
        rpcSocial("responder_desafio", "{\"p_challenge\":${quote(desafioId)},\"p_accept\":$aceitar}")

    override suspend fun cancelarDesafio(desafioId: String): Result<Unit> =
        rpcSocial("cancelar_desafio", "{\"p_challenge\":${quote(desafioId)}}")

    override suspend fun apagarMeusDados(): Result<Unit> =
        withContext(Dispatchers.IO) {
            rede {
                val token = auth.accessTokenValido()
                    ?: error("Entre na Arena Global para apagar seus dados remotos.")
                rpc("apagar_meus_dados", "{}", token).use { resp ->
                    check(resp.isSuccessful) { "Não foi possível apagar os dados remotos (HTTP ${resp.code})." }
                }
            }
        }

    private suspend fun rpc(nome: String, corpo: String, token: String) = client.newCall(
        Request.Builder()
            .url("${base()}/rest/v1/rpc/$nome")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .post(corpo.toRequestBody(json))
            .build(),
    ).await()

    private suspend inline fun <reified R, T> listaRpc(
        nome: String,
        crossinline converter: (R) -> T,
    ): Result<List<T>> = withContext(Dispatchers.IO) {
        rede {
            val token = auth.accessTokenValido() ?: error("Entre na Arena Global para continuar.")
            rpc(nome, "{}", token).use { resp ->
                val corpo = resp.body?.string() ?: ""
                check(resp.isSuccessful) { mensagemSocial(resp.code, corpo) }
                val tipo = Types.newParameterizedType(List::class.java, R::class.java)
                val linhas: List<R> = moshi.adapter<List<R>>(tipo).fromJson(corpo) ?: emptyList()
                linhas.map(converter)
            }
        }
    }

    private suspend fun rpcSocial(nome: String, corpo: String): Result<Unit> = withContext(Dispatchers.IO) {
        rede {
            val token = auth.accessTokenValido() ?: error("Entre na Arena Global para continuar.")
            rpc(nome, corpo, token).use { resp ->
                val resposta = resp.body?.string() ?: ""
                check(resp.isSuccessful) { mensagemSocial(resp.code, resposta) }
            }
        }
    }

    private fun mensagemSocial(code: Int, corpo: String): String = when {
        code == 401 || code == 403 -> "Sua sessão da Arena expirou. Entre novamente."
        code == 404 -> "A Arena Social ainda não está disponível no servidor."
        corpo.contains("friendship already exists", true) -> "Já existe um pedido ou amizade com este jogador."
        corpo.contains("challenge already exists", true) -> "Já existe um duelo aberto com este jogador."
        corpo.contains("publish ranking first", true) -> "Publique seu ranking antes de iniciar o duelo."
        corpo.contains("player not found", true) -> "Este jogador ainda não publicou um perfil."
        else -> "A Arena não concluiu esta ação (HTTP $code)."
    }

    /** Maps transport failures to a message the UI can show without leaking internals. */
    private inline fun <T> rede(block: () -> T): Result<T> = runCatching(block).recoverCatching { e ->
        throw if (e is IOException) IOException("Sem conexão com a Arena Global. Tente de novo com internet.", e) else e
    }

    /** Escapes pasted names and invitation codes before building the RPC payload. */
    private fun quote(s: String): String = JSONObject.quote(s)
}
