package com.liferpg.data

import android.content.Context
import com.liferpg.domain.EstadoSessao
import com.liferpg.domain.Exercicio
import com.liferpg.domain.ItemSessao
import com.liferpg.domain.ItemTreino
import com.liferpg.domain.Musculo
import com.liferpg.domain.Patente
import com.liferpg.domain.PatenteSubida
import com.liferpg.domain.SerieFeita
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class ActiveSetSnapshot(
    val pesoKg: Float? = null,
    val reps: Int? = null,
    val segundos: Int? = null,
    val recorde: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ActiveItemSnapshot(
    val exercicioId: String,
    val series: Int,
    val repMin: Int,
    val repMax: Int,
    val descansoSeg: Int,
    val motivo: String,
    val feitas: List<ActiveSetSnapshot> = emptyList(),
    val conclusaoManual: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class ActiveRankSnapshot(val musculo: String, val patente: String)

@JsonClass(generateAdapter = true)
data class ActiveWorkoutSnapshot(
    val version: Int = 1,
    val nome: String,
    val itens: List<ActiveItemSnapshot>,
    val indiceAtual: Int,
    val iniciadaEm: Long,
    val xpGanho: Int,
    val recordes: List<String> = emptyList(),
    val patentesSubidas: List<ActiveRankSnapshot> = emptyList(),
    val concluida: Boolean = false,
    val sessionUuid: String? = null,
)

fun EstadoSessao.toActiveSnapshot() = ActiveWorkoutSnapshot(
    nome = nome,
    itens = itens.map { sessionItem ->
        val item = sessionItem.item
        ActiveItemSnapshot(
            exercicioId = item.exercicio.id,
            series = item.series,
            repMin = item.repMin,
            repMax = item.repMax,
            descansoSeg = item.descansoSeg,
            motivo = item.motivo,
            feitas = sessionItem.feitas.map { ActiveSetSnapshot(it.pesoKg, it.reps, it.segundos, it.recorde) },
            conclusaoManual = sessionItem.conclusaoManual,
        )
    },
    indiceAtual = indiceAtual,
    iniciadaEm = iniciadaEm,
    xpGanho = xpGanho,
    recordes = recordes,
    patentesSubidas = patentesSubidas.map { ActiveRankSnapshot(it.musculo.name, it.patente.name) },
    concluida = concluida,
    sessionUuid = sessionUuid,
)

fun ActiveWorkoutSnapshot.resolve(catalog: List<Exercicio>): EstadoSessao? {
    if (version != 1) return null
    val byId = catalog.associateBy { it.id }
    val resolved = itens.mapNotNull { saved ->
        val exercise = byId[saved.exercicioId] ?: return@mapNotNull null
        val min = saved.repMin.coerceIn(1, 999)
        ItemSessao(
            item = ItemTreino(
                exercicio = exercise,
                series = saved.series.coerceIn(1, 20),
                repMin = min,
                repMax = saved.repMax.coerceIn(min, 999),
                descansoSeg = saved.descansoSeg.coerceIn(15, 600),
                motivo = saved.motivo.take(120),
            ),
            feitas = saved.feitas.take(100).map {
                SerieFeita(it.pesoKg, it.reps, it.segundos, it.recorde)
            },
            conclusaoManual = saved.conclusaoManual,
        )
    }
    if (resolved.isEmpty()) return null
    return EstadoSessao(
        nome = nome.take(80).ifBlank { "Treino em andamento" },
        itens = resolved,
        indiceAtual = indiceAtual.coerceIn(resolved.indices),
        iniciadaEm = iniciadaEm,
        xpGanho = xpGanho.coerceAtLeast(0),
        recordes = recordes.take(100),
        patentesSubidas = patentesSubidas.mapNotNull { saved ->
            runCatching { PatenteSubida(Musculo.valueOf(saved.musculo), Patente.valueOf(saved.patente)) }.getOrNull()
        },
        concluida = concluida,
        sessionUuid = sessionUuid?.takeIf { runCatching { java.util.UUID.fromString(it) }.isSuccess }
            ?: java.util.UUID.nameUUIDFromBytes(("legacy-session:" + iniciadaEm + ":" + nome).toByteArray()).toString(),
    )
}

class ActiveWorkoutStore(context: Context) {
    private val preferences = context.getSharedPreferences("active_workout_state", Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder().build().adapter(ActiveWorkoutSnapshot::class.java)

    fun save(session: EstadoSessao?) {
        val edit = preferences.edit()
        if (session == null) edit.remove(KEY_SESSION)
        else edit.putString(KEY_SESSION, adapter.toJson(session.toActiveSnapshot()))
        edit.commit()
    }

    fun restore(catalog: List<Exercicio>): EstadoSessao? {
        val json = preferences.getString(KEY_SESSION, null) ?: return null
        val session = runCatching { adapter.fromJson(json)?.resolve(catalog) }.getOrNull()
        if (session == null) preferences.edit().remove(KEY_SESSION).apply()
        return session
    }

    fun saveRest(endEpochMs: Long?, durationSeconds: Int) {
        val edit = preferences.edit()
        if (endEpochMs == null) edit.remove(KEY_REST_END).remove(KEY_REST_DURATION)
        else edit.putLong(KEY_REST_END, endEpochMs).putInt(KEY_REST_DURATION, durationSeconds)
        edit.commit()
    }

    fun restoreRest(nowEpochMs: Long = System.currentTimeMillis()): Pair<Long, Int>? {
        val end = preferences.getLong(KEY_REST_END, 0L)
        if (end <= nowEpochMs) {
            saveRest(null, 90)
            return null
        }
        return end to preferences.getInt(KEY_REST_DURATION, 90).coerceIn(15, 600)
    }

    private companion object {
        const val KEY_SESSION = "session_json_v1"
        const val KEY_REST_END = "rest_end_epoch_ms"
        const val KEY_REST_DURATION = "rest_duration_seconds"
    }
}
