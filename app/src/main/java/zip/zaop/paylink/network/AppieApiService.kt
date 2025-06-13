package zip.zaop.paylink.network

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

private const val BASE_URL = "https://api.ah.nl/mobile-services/"

@OptIn(ExperimentalSerializationApi::class)
private val JsonCool = Json { ignoreUnknownKeys = true; explicitNulls = false }

private val retrofit = Retrofit.Builder()
    .addConverterFactory(
        @OptIn(ExperimentalSerializationApi::class)
        JsonCool.asConverterFactory("application/json".toMediaType())
    )
    .baseUrl(BASE_URL)
    .build()

interface AppieApiService {
    @GET("v1/receipts")
    suspend fun getReceipts(
        @Header("Authorization") auth: String
    ): List<AppieReceiptListItem>

    @GET("v2/receipts/{id}")
    suspend fun getReceipt(
        @Path("id") id: String,
        @Header("Authorization") auth: String
    ): AppieReceiptDetailsResponse

}

object AppieApi {
    @Volatile
    private var INSTANCE: AppieApiService? = null

    fun getRetrofitService(context: Context): AppieApiService =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: newRetrofit(
                context,
                BASE_URL,
                AppieApiService::class.java,
                mapOf()
            )
                .also { INSTANCE = it }
        }
}
