package com.liferpg.data.net

import com.liferpg.domain.AlimentoEncontrado
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class UsdaNutrient(val nutrientNumber: String?, val value: Double?)

@JsonClass(generateAdapter = true)
data class UsdaFood(val description: String?, val foodNutrients: List<UsdaNutrient>?)

@JsonClass(generateAdapter = true)
data class UsdaSearchResponse(val foods: List<UsdaFood>?)

interface UsdaApi {
    @GET("fdc/v1/foods/search")
    suspend fun buscar(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("pageSize") pageSize: Int = 15,
        @Query("dataType") dataType: String = "Foundation,SR Legacy",
    ): UsdaSearchResponse
}

object Usda {

    const val API_KEY = "DEMO_KEY"

    private val api: UsdaApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.nal.usda.gov/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(com.squareup.moshi.Moshi.Builder().build()))
            .build()
            .create(UsdaApi::class.java)
    }

    // No withContext here: the DEX shows a plain ContinuationImpl and no $2 lambda class,
    // unlike every other repo in this package.
    suspend fun buscar(termo: String): List<AlimentoEncontrado> =
        api.buscar(termo, API_KEY).foods.orEmpty().mapNotNull { it.paraDominio() }

    private fun UsdaFood.paraDominio(): AlimentoEncontrado? {
        fun n(num: String): Float =
            (foodNutrients?.firstOrNull { it.nutrientNumber == num }?.value ?: 0.0).toFloat()

        val nome = description?.takeIf { it.isNotBlank() } ?: return null
        val cal = n("208")
        val prot = n("203")
        if (cal <= 0f && prot <= 0f) return null
        return AlimentoEncontrado(
            nome.lowercase().replaceFirstChar { it.uppercase() },
            "USDA",
            prot,
            cal,
            n("205"),
            n("204"),
            null,
        )
    }
}
