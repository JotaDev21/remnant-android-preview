package com.liferpg.data.net

import com.liferpg.domain.ExercicioRef
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class WgerCategoria(val id: Int, val nome: String)

@JsonClass(generateAdapter = true)
data class WgerCat(val name: String?)

@JsonClass(generateAdapter = true)
data class WgerImg(val image: String?)

@JsonClass(generateAdapter = true)
data class WgerTrans(val language: Int?, val name: String?)

@JsonClass(generateAdapter = true)
data class WgerInfo(
    val category: WgerCat?,
    val images: List<WgerImg>?,
    val translations: List<WgerTrans>?,
)

@JsonClass(generateAdapter = true)
data class WgerInfoResp(val results: List<WgerInfo>?)

internal interface WgerApi {
    @GET("api/v2/exerciseinfo/")
    suspend fun porCategoria(
        @Query("category") category: Int,
        @Query("language") language: Int = 2,
        @Query("limit") limit: Int = 60,
        @Query("format") format: String = "json",
    ): WgerInfoResp
}

object WgerRepo {

    val CATEGORIAS = listOf(
        WgerCategoria(11, "Peito"),
        WgerCategoria(12, "Costas"),
        WgerCategoria(9, "Pernas"),
        WgerCategoria(13, "Ombros"),
        WgerCategoria(8, "Braços"),
        WgerCategoria(10, "Abdômen"),
        WgerCategoria(14, "Panturrilha"),
        WgerCategoria(15, "Cardio"),
    )

    private val api: WgerApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://wger.de/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(com.squareup.moshi.Moshi.Builder().build()))
            .build()
            .create(WgerApi::class.java)
    }

    suspend fun porCategoria(categoriaId: Int): Result<List<ExercicioRef>> = withContext(Dispatchers.IO) {
        runCatching {
            api.porCategoria(categoriaId).results.orEmpty()
                .mapNotNull { info ->
                    val nome = info.translations
                        ?.firstOrNull { it.language == 2 && !it.name.isNullOrBlank() }
                        ?.name
                        ?: return@mapNotNull null
                    ExercicioRef(
                        nome,
                        info.category?.name ?: "",
                        info.images?.firstOrNull { !it.image.isNullOrBlank() }?.image,
                    )
                }
                .distinctBy { it.nome }
        }
    }
}
