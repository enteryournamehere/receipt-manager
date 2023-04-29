package zip.zaop.paylink.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path

private const val BASE_URL = "https://tickets.lidlplus.com/api/v1/NL/"

private val JsonCool = Json { ignoreUnknownKeys = true }

private val retrofit = Retrofit.Builder()
    .addConverterFactory(JsonCool.asConverterFactory(MediaType.get("application/json")))
    .baseUrl(BASE_URL)
    .build()

interface LidlApiService {
    @Headers(
        "App-Version: 999.99.9",
        "Operating-System: iOS",
        "App: com.lidl.eci.lidl.plus",
        "Accept-Language: NL"
    )
    @GET("list/{page}")
    suspend fun getReceipts(@Path("page") page: Int, @Header("Authorization") auth: String): ReceiptListResponse
}

object LidlApi {
    val retrofitService : LidlApiService by lazy {
        retrofit.create(LidlApiService::class.java)
    }
}