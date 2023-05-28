package zip.zaop.paylink.network

import android.content.Context
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

private const val BASE_URL = "https://app.wiebetaaltwat.nl/api/"

interface WbwApiService {
    @POST("users/sign_in")
    suspend fun logIn(
        @Body data: LoginRequest
    ): LoginResponse

    @GET("lists")
    suspend fun getLists(): PaginationResponse<ListListResponseItem>

    @GET("lists/{list}/members")
    suspend fun getMembers(@Path("list") list: String): PaginationResponse<ListMemberListResponseItem>

    @POST("lists/{list}/expenses")
    suspend fun addExpense(@Path("list") list: String, @Body expense: NewExpenseWrapper): NewExpenseResponse

    @GET("balances")
    suspend fun getBalances(): NoPaginationResponse<BalancesResponseItem>
}

object WbwApi {
    @Volatile
    private var INSTANCE: WbwApiService? = null;

    fun getRetrofitService(context: Context): WbwApiService =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: newRetrofit(
                context,
                BASE_URL,
                WbwApiService::class.java,
                mapOf(
                    "Accept-Version" to "10",
                    "Content-Type" to "application/json",
                    "Accept" to "application/json"
                )
            )
                .also { INSTANCE = it }
        }
}
