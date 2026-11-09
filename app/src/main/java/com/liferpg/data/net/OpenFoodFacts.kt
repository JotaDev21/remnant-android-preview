package com.liferpg.data.net

import android.content.Context
import com.liferpg.domain.AlimentoEncontrado
import com.liferpg.domain.NutritionFoodCodec
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val CAMPOS = "code,product_name,brands,nutriments,image_front_small_url,no_nutrition_data"

@JsonClass(generateAdapter = true)
data class OffNutriments(
    @Json(name = "proteins_100g") val proteins100g: Double?,
    @Json(name = "energy-kcal_100g") val energyKcal100g: Double?,
    @Json(name = "carbohydrates_100g") val carbs100g: Double?,
    @Json(name = "fat_100g") val fat100g: Double?,
    @Json(name = "fiber_100g") val fiber100g: Double? = null,
)

@JsonClass(generateAdapter = true)
data class OffProduct(
    val code: String?,
    @Json(name = "product_name") val productName: String?,
    val brands: String?,
    val nutriments: OffNutriments?,
    @Json(name = "image_front_small_url") val imageFrontSmallUrl: String? = null,
    @Json(name = "no_nutrition_data") val noNutritionData: String? = null,
)

@JsonClass(generateAdapter = true)
data class OffSearchResponse(val count: Int?, val products: List<OffProduct>?)

@JsonClass(generateAdapter = true)
data class OffProductResponse(val product: OffProduct?)

interface OpenFoodFactsApi {
    // v2 search is filter-only: the documented legacy endpoint supports names/brands.
    @GET("cgi/search.pl?search_simple=1&action=process&json=1")
    suspend fun buscar(
        @Query("search_terms") termo: String,
        @Query("fields") fields: String = CAMPOS,
        @Query("page_size") pageSize: Int = 20,
    ): OffSearchResponse

    @GET("api/v3.6/product/{barcode}.json")
    suspend fun produto(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = CAMPOS,
    ): OffProductResponse
}

internal fun OffProduct.paraDominio(): AlimentoEncontrado? {
    val n = nutriments ?: return null
    if (noNutritionData == "on") return null
    val nome = productName?.trim()?.takeIf { it.isNotBlank() }?.take(160) ?: return null
    fun known(v: Double?, max: Double) = v?.takeIf { it.isFinite() && it in 0.0..max }
    val p = known(n.proteins100g, 100.0); val c = known(n.carbs100g, 100.0)
    val g = known(n.fat100g, 100.0); val kcal = known(n.energyKcal100g, 1000.0)
    if (listOf(p, c, g, kcal).all { it == null }) return null
    return AlimentoEncontrado(
        nome,
        brands?.takeIf { it.isNotBlank() },
        (p ?: 0.0).toFloat(),
        (kcal ?: 0.0).toFloat(),
        (c ?: 0.0).toFloat(),
        (g ?: 0.0).toFloat(),
        code,
        id = "off:${code.orEmpty()}", fonte = "Open Food Facts · colaborativo",
        fibraG = known(n.fiber100g, 100.0)?.toFloat(),
        imagemUrl = imageFrontSmallUrl?.takeIf { it.startsWith("https://") },
        dadosAusentes = (if (p == null) 1 else 0) or (if (kcal == null) 2 else 0) or (if (c == null) 4 else 0) or (if (g == null) 8 else 0),
    )
}

data class BuscaNutricional(val alimentos: List<AlimentoEncontrado>, val aviso: String? = null)

class NutricaoRepo(context: Context? = null, private val api: OpenFoodFactsApi = criarApi()) {
    val brasileiros = context?.let(::BrazilianFoods)
    private val cacheDir = context?.let { File(it.cacheDir, "off_food_cache").apply { mkdirs() } }
    private val networkLock = Mutex()
    private var lastSearch = 0L
    private var lastBarcode = 0L

    companion object {
    internal fun criarApi(baseUrl: String = "https://world.openfoodfacts.org/"): OpenFoodFactsApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Remnant/2.1 (https://github.com/JotaDev21/remnant-updates)")
                        .build()
                )
            }
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(com.squareup.moshi.Moshi.Builder().build()))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
    }

    suspend fun buscar(termo: String, online: Boolean = false): Result<BuscaNutricional> = withContext(Dispatchers.IO) {
        val local = brasileiros?.buscar(termo).orEmpty().take(60)
        if (!online) return@withContext Result.success(BuscaNutricional(local))
        val key = "search:${termo.trim().lowercase().take(80)}"
        val cached = lerCache(key)
        if (cached != null) return@withContext Result.success(BuscaNutricional(local + cached))
        try {
            val off = networkLock.withLock {
                delay((6500 - (System.currentTimeMillis() - lastSearch)).coerceAtLeast(0))
                lastSearch = System.currentTimeMillis()
                api.buscar(termo.take(80)).products.orEmpty().mapNotNull { it.paraDominio() }.take(20)
            }
            guardarCache(key, off)
            Result.success(BuscaNutricional((local + off).distinctBy { it.id.ifBlank { it.nome.lowercase() } }))
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            val old = lerCache(key, permitirAntigo = true).orEmpty()
            Result.success(BuscaNutricional(local + old, if (old.isEmpty()) "A busca online está indisponível. A base brasileira continua funcionando."
                else "Sem conexão: mostrando produtos consultados anteriormente. Confira o rótulo."))
        }
    }

    suspend fun porBarcode(barcode: String): Result<AlimentoEncontrado?> = withContext(Dispatchers.IO) {
        if (!barcode.matches("[0-9]{8,14}".toRegex())) return@withContext Result.failure(IllegalArgumentException("Código inválido."))
        val key = "barcode:$barcode"
        lerCache(key)?.firstOrNull()?.let { return@withContext Result.success(it) }
        try {
            val food = networkLock.withLock {
                delay((4200 - (System.currentTimeMillis() - lastBarcode)).coerceAtLeast(0))
                lastBarcode = System.currentTimeMillis()
                api.produto(barcode).product?.paraDominio()
            }
            if (food != null) guardarCache(key, listOf(food))
            Result.success(food)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { lerCache(key, permitirAntigo = true)?.firstOrNull()?.let { Result.success(it) } ?: Result.failure(e) }
    }

    private fun arquivo(key: String): File? = cacheDir?.let { dir ->
        File(dir, MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
    }
    private fun lerCache(key: String, permitirAntigo: Boolean = false): List<AlimentoEncontrado>? = runCatching {
        val file = arquivo(key) ?: return null
        if (!file.isFile || file.length() > 256_000 || !permitirAntigo && System.currentTimeMillis() - file.lastModified() > 7 * 86_400_000L) return null
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { NutritionFoodCodec.ler(arr.getString(it)) }
    }.getOrNull()
    private fun guardarCache(key: String, items: List<AlimentoEncontrado>) {
        runCatching {
            arquivo(key)?.writeText(JSONArray().apply { items.forEach { put(NutritionFoodCodec.escrever(it)) } }.toString())
            cacheDir?.listFiles()?.sortedByDescending { it.lastModified() }?.drop(60)?.forEach { it.delete() }
        }
    }
}
