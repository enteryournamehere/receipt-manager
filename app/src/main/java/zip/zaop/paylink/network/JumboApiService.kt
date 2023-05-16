package zip.zaop.paylink.network

import android.content.Context
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Path

private const val BASE_URL = "https://loyalty-app.jumbo.com/api/"

interface JumboApiService {
    @GET("receipt/customer/overviews")
    @Headers("Accept: */*")
    suspend fun getReceipts(
        @Header("Authorization") auth: String
    ): List<JumboReceiptListItem>

    @GET("user/profile")
    @Headers("Accept: */*")
    suspend fun getProfile(
        @Header("Authorization") auth: String
    ): JumboUserProfile

    // pretty sure this returns 412 if captcha header is invalid :_)
    @GET("receipt/{id}")
    @Headers("Accept: */*")
    suspend fun getReceipt(
        @Path("id") id: String,
        @Header("Authorization") auth: String
    ): JumboReceipt
}

object JumboApi {
    @Volatile
    private var INSTANCE: JumboApiService? = null;

    fun getRetrofitService(context: Context): JumboApiService =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: newRetrofit(context, BASE_URL, "jumbo/1.2.3", JumboApiService::class.java)
                .also { INSTANCE = it }
        }
}
