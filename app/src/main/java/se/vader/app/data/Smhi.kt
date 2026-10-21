package se.vader.app.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import se.vader.app.BuildConfig
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * SMHI:s punktprognos, modell SNOW1g (ersatte PMP3g som stängdes 31 mars 2026).
 * https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/{lon}/lat/{lat}/data.json
 *
 * Tidsserien: timvis ca 2 dygn, därefter 2-, 6- och 12-timmarssteg, totalt ca 10 dygn.
 * Nederbörd (precipitation_amount_mean) är mängd i mm under intervallet intervalParametersStartTime -> time.
 * Gson ignorerar Kotlins null-säkerhet, därför är allt nullable.
 */
data class SmhiForecast(
    val createdTime: String?,
    val referenceTime: String?,
    val timeSeries: List<SmhiStep>?
)

data class SmhiStep(
    val time: String?,
    val intervalParametersStartTime: String?,
    val data: SmhiData?
)

data class SmhiData(
    @SerializedName("air_temperature") val airTemperature: Double?,
    @SerializedName("wind_speed") val windSpeed: Double?,
    @SerializedName("wind_speed_of_gust") val windGust: Double?,
    @SerializedName("precipitation_amount_mean") val precipitationMean: Double?,
    @SerializedName("probability_of_precipitation") val precipitationProbability: Double?,
    @SerializedName("thunderstorm_probability") val thunderProbability: Double?,
    @SerializedName("symbol_code") val symbolCode: Double?,
    @SerializedName("cloud_area_fraction") val cloudOktas: Double?
)

interface SmhiApi {
    @GET("api/category/snow1g/version/1/geotype/point/lon/{lon}/lat/{lat}/data.json")
    suspend fun pointForecast(
        @Path("lon", encoded = true) lon: String,
        @Path("lat", encoded = true) lat: String
    ): SmhiForecast
}

/** Fel som UI:t kan visa direkt. */
class ForecastException(message: String) : Exception(message)

class SmhiRepository(private val api: SmhiApi = SmhiModule.api) {

    suspend fun forecast(lat: Double, lon: Double): SmhiForecast {
        try {
            return api.pointForecast(lon.fmt(), lat.fmt())
        } catch (e: HttpException) {
            throw ForecastException(
                if (e.code() == 404) "SMHI har ingen prognos för den platsen (bara Norden täcks)."
                else "SMHI svarade med fel ${e.code()}. Försök igen om en stund."
            )
        }
    }

    // Punkt som decimalavskiljare oavsett telefonens språk, max 4 decimaler (~10 m).
    private fun Double.fmt() = String.format(Locale.US, "%.4f", this)
}

object SmhiModule {
    private const val BASE_URL = "https://opendata-download-metfcst.smhi.se/"

    val api: SmhiApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Vader7-Android/${BuildConfig.VERSION_NAME}")
                        .build()
                )
            }
            .build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmhiApi::class.java)
    }
}
