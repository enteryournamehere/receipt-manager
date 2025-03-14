package zip.zaop.paylink.network

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

private const val BASE_URL = "https://tickets.lidlplus.com/api/"

private val JsonCool = Json { ignoreUnknownKeys = true }

private val retrofit = Retrofit.Builder()
    .addConverterFactory(
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        JsonCool.asConverterFactory("application/json".toMediaType())
    )
    .baseUrl(BASE_URL)
    .build()

interface LidlApiService {
    @Headers(
        "App-Version: 999.99.9", //15.19.1
        "Operating-System: iOS", //Android
        "App: com.lidl.eci.lidlplus",
        "Accept-Language: NL"
    )
    @GET("v2/NL/tickets")
    suspend fun getReceipts(
        @Query("pageNumber") page: Int,
        @Header("Authorization") auth: String,
        @Query("onlyFavorite") onlyFavorite: Boolean = false,
        // @Query("itemId") = ???
    ): NetworkLidlReceiptList

    @Headers(
        "App-Version: 999.99.9",
        "Operating-System: iOS",
        "App: com.lidl.eci.lidlplus",
        "Accept-Language: NL"
    )
    @GET("v3/NL/tickets/{id}")
    suspend fun getReceipt(
        @Path("id") id: String,
        @Header("Authorization") auth: String
    ): NetworkLidlReceiptDetails

}

object LidlApi {
    @Volatile
    private var INSTANCE: LidlApiService? = null

    fun getRetrofitService(context: Context): LidlApiService =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: newRetrofit(
                context,
                BASE_URL,
                LidlApiService::class.java,
                mapOf()
            )
                .also { INSTANCE = it }
        }
}
