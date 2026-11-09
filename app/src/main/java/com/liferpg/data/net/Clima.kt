package com.liferpg.data.net

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class Clima(
    val tempC: Int,
    val chovendo: Boolean,
    val cidade: String,
    val emoji: String,
    val descricao: String,
)

@JsonClass(generateAdapter = true)
data class IpLoc(
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val success: Boolean?,
)

@JsonClass(generateAdapter = true)
data class MeteoCurrent(
    @Json(name = "temperature_2m") val temp: Double?,
    val precipitation: Double?,
    @Json(name = "weather_code") val weatherCode: Int?,
)

@JsonClass(generateAdapter = true)
data class MeteoResp(val current: MeteoCurrent?)

internal interface IpApi {
    @GET("/")
    suspend fun loc(): IpLoc
}

internal interface MeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = "temperature_2m,precipitation,weather_code",
    ): MeteoResp
}

object ClimaRepo {

    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val moshi = MoshiConverterFactory.create(com.squareup.moshi.Moshi.Builder().build())

    private val ip: IpApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipwho.is/")
            .client(client)
            .addConverterFactory(moshi)
            .build()
            .create(IpApi::class.java)
    }

    private val meteo: MeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(moshi)
            .build()
            .create(MeteoApi::class.java)
    }

    private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)

    suspend fun obter(): Result<Clima?> = withContext(Dispatchers.IO) {
        runCatching {
            val loc = ip.loc()
            val lat = loc.latitude
            val lon = loc.longitude
            if (lat == null || lon == null) return@runCatching null
            val cur = meteo.forecast(lat, lon).current ?: return@runCatching null
            val code = cur.weatherCode ?: 0
            // INFERRED: jadx renders this two-clause condition degenerately across the
            // duplicated switch cases. Both clauses are present in the bytecode; the OR
            // is the only reading consistent with RAIN_CODES being used at all.
            val chovendo = (cur.precipitation ?: 0.0) > 0.1 || code in RAIN_CODES
            Clima(
                (cur.temp ?: 0.0).toInt(),
                chovendo,
                loc.city ?: "",
                emojiDoCodigo(code, chovendo),
                descricaoDoCodigo(code),
            )
        }
    }

    private fun emojiDoCodigo(code: Int, chovendo: Boolean): String = when {
        code in 95..99 -> "⛈️"
        chovendo -> "🌧️"
        code in 71..77 || code == 85 || code == 86 -> "❄️"
        code in 1..3 -> "⛅"
        code == 45 || code == 48 -> "🌫️"
        else -> "☀️"
    }

    private fun descricaoDoCodigo(code: Int): String = when {
        code == 0 -> "Céu limpo"
        code in 1..3 -> "Parcialmente nublado"
        code == 45 || code == 48 -> "Névoa"
        code in 51..67 -> "Chuva"
        code in 71..86 -> "Neve"
        code in 95..99 -> "Tempestade"
        else -> "Tempo instável"
    }
}
