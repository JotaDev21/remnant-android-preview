package com.liferpg.data.net

import com.liferpg.domain.Receita
import com.liferpg.domain.RecipeInstructions
import com.liferpg.domain.VideoReceita
import com.liferpg.domain.normalizarAlimento
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class MealDbResponse(val meals: List<Map<String, String?>>?)

interface MealDbApi {
    @GET("api/json/v1/1/search.php")
    suspend fun buscar(@Query("s") termo: String): MealDbResponse

    @GET("api/json/v1/1/filter.php")
    suspend fun porCategoria(@Query("c") categoria: String): MealDbResponse
}

object Receitas {

    private val api: MealDbApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://www.themealdb.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(com.squareup.moshi.Moshi.Builder().build()))
            .build()
            .create(MealDbApi::class.java)
    }

    suspend fun buscar(termo: String, servico: MealDbApi = api): Result<List<Receita>> = withContext(Dispatchers.IO) {
        try { Result.success(servico.buscar(termoOnline(termo)).meals.orEmpty().mapNotNull { it.paraReceita() }.distinctBy { it.id }) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    internal fun termoOnline(termo: String): String {
        val common = mapOf("frango" to "chicken", "arroz" to "rice", "aveia" to "oats", "ovo" to "egg", "ovos" to "egg",
            "carne" to "beef", "bife" to "beef", "peixe" to "fish", "atum" to "tuna", "sardinha" to "sardine",
            "lentilha" to "lentil", "batata" to "potato", "batata doce" to "sweet potato", "feijao" to "bean",
            "banana" to "banana", "mamao" to "papaya", "salada" to "salad", "macarrao" to "pasta")
        return common[normalizarAlimento(termo)] ?: termo.trim().take(100)
    }

    private fun Map<String, String?>.paraReceita(): Receita? {
        val nome = this["strMeal"]?.takeIf { it.isNotBlank() } ?: return null
        val instrucoes = this["strInstructions"]?.takeIf { it.isNotBlank() } ?: return null
        val id = this["idMeal"]?.takeIf { it.matches(Regex("[0-9]+")) } ?: return null
        val ingredientes = (1..20).mapNotNull { i ->
            val ing = this["strIngredient$i"]?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val medida = this["strMeasure$i"]?.trim() ?: ""
            if (medida.isNotBlank()) "$medida $ing" else ing
        }
        if (ingredientes.isEmpty()) return null
        return Receita(
            "mealdb:$id",
            nome,
            this["strMealThumb"]?.takeIf { it.startsWith("https://") },
            instrucoes,
            ingredientes,
            etapas = RecipeInstructions.etapas(instrucoes),
            videos = RecipeInstructions.youtube(this["strYoutube"])?.let { listOf(VideoReceita("Vídeo desta receita", it, "Indicado pelo TheMealDB")) }.orEmpty(),
            fonteUrl = RecipeInstructions.https(this["strSource"]) ?: "https://www.themealdb.com/meal/$id",
            tags = listOfNotNull(this["strCategory"], this["strArea"]).filter { it.isNotBlank() },
            idioma = "en",
        )
    }
}
