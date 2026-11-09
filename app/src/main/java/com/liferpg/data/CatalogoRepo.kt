package com.liferpg.data

import android.content.Context
import android.util.Log
import com.liferpg.domain.CatalogoExerciciosJsonAdapter
import com.liferpg.domain.Exercicio
import com.liferpg.domain.Musculo
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the bundled `exercises.json` once and keeps it in memory. A corrupt catalogue is
 * never fatal: the app falls back to an empty list and logs.
 */
class CatalogoRepo(private val context: Context) {

    @Volatile
    private var cache: List<Exercicio>? = null

    suspend fun exercicios(): List<Exercicio> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            cache?.let { return@withContext it }
            val resultado = runCatching {
                // A referência direta mantém o adapter no APK otimizado e faz a release
                // carregar exatamente o mesmo catálogo que a Preview.
                val adapter = CatalogoExerciciosJsonAdapter(MOSHI)
                val lista = listOf("exercises.json", "exercises_gym.json").flatMap { arquivo ->
                    val texto = context.assets.open(arquivo).bufferedReader().use { it.readText() }
                    requireNotNull(adapter.fromJson(texto)) { "Catálogo inválido: $arquivo" }.exercicios
                }.map(::sanear)
                require(lista.size >= MIN_CATALOG_SIZE) { "Catálogo incompleto: ${lista.size}" }
                require(lista.map { it.id }.distinct().size == lista.size) { "IDs de exercícios repetidos" }
                lista
            }
            resultado.exceptionOrNull()?.let {
                Log.e("CatalogoRepo", "Falha ao carregar exercises.json", it)
            }
            val lista = resultado.getOrDefault(emptyList())
            cache = lista
            lista
        }
    }

    suspend fun porId(id: String): Exercicio? = exercicios().firstOrNull { it.id == id }

    suspend fun porMusculo(): Map<Musculo, List<Exercicio>> = exercicios().groupBy { it.musculo }

    /** Clamps difficulty and drops a benchmark ladder that is not 7 strictly ascending positives. */
    private fun sanear(ex: Exercicio): Exercicio {
        val dif = ex.dificuldade.coerceIn(0.5f, 3f)
        val benchmarks = ex.benchmarks?.takeIf { b ->
            b.size == 7 && b.all { it > 0f } && (0 until 6).all { b[it] < b[it + 1] }
        }
        return if (dif == ex.dificuldade && benchmarks === ex.benchmarks) {
            ex
        } else {
            ex.copy(dificuldade = dif, benchmarks = benchmarks)
        }
    }

    companion object {
        private val MOSHI: Moshi = Moshi.Builder().build()
        internal const val MIN_CATALOG_SIZE = 390

        const val BASE_URL_IMAGENS =
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

        fun urlImagem(caminho: String): String = BASE_URL_IMAGENS + caminho
    }
}
